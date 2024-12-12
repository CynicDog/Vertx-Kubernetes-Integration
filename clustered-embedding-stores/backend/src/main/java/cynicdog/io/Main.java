package cynicdog.io;

import cynicdog.io.api.OllamaAPI;
import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;


public class Main extends AbstractVerticle {

    private static final String OLLAMA_HOST = System.getenv().getOrDefault("OLLAMA_HOST", "localhost");
    private static final int OLLAMA_PORT = Integer.parseInt(System.getenv().getOrDefault("OLLAMA_PORT", "11434"));
    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    public static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static DefaultCacheManager cacheManager;
    private static ClusterManager clusterManager;
    private Cache<String, float[]> embeddingsCache;
    private WebClient client;

    @Override
    public void start() throws Exception {

        client = WebClient.create(vertx);
        cacheManager = new DefaultCacheManager();
        clusterManager = new InfinispanClusterManager(cacheManager);

        // Configure the cache for embeddings
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.encoding()
                .mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .memory();

        // Initialize the cache for embeddings
        embeddingsCache = cacheManager.createCache("embeddings", builder.build());

        // Initialize Ollama API
        var OllamaAPI = new OllamaAPI(vertx, OLLAMA_HOST, OLLAMA_PORT, embeddingsCache);

        // Register router handlers
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false))));

        router.post("/embed").handler(OllamaAPI::embed);
        router.post("/generate").handler(OllamaAPI::generate);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    public static void main(String[] args) {
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}
