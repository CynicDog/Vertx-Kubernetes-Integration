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
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import java.util.function.Function;


public class Main extends AbstractVerticle {

    private static final String OLLAMA_HOST = System.getenv().getOrDefault("OLLAMA_HOST", "localhost");
    private static final int OLLAMA_PORT = Integer.parseInt(System.getenv().getOrDefault("OLLAMA_PORT", "11434"));
    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    public static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static ClusterManager clusterManager;

    @Override
    public void start() throws Exception {

        DefaultCacheManager cacheManager = new DefaultCacheManager(
                new GlobalConfigurationBuilder()
                        .transport()
                        .defaultTransport()
                        .build()
        );
        clusterManager = new InfinispanClusterManager(cacheManager);

        // Configure the cache for embeddings
        ConfigurationBuilder cacheConfig = new ConfigurationBuilder();
        cacheConfig.clustering()
                .cacheMode(CacheMode.REPL_ASYNC)
                .encoding()
                .mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .memory();

        // Initialize Ollama API
        var OllamaAPI = new OllamaAPI(vertx, OLLAMA_HOST, OLLAMA_PORT, cacheManager, cacheConfig);

        // Register router handlers
        Router router = Router.router(vertx);

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false))));

        registerEventBusConsumer("embed", OllamaAPI::embed);
        registerEventBusConsumer("evict", OllamaAPI::evict);
        registerEventBusConsumer("evictAll", OllamaAPI::evictAll);
        registerEventBusConsumer("generate", OllamaAPI::generate);

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

    private void registerEventBusConsumer(String address, Function<String, Future<String>> action) {
        vertx.eventBus().<String>consumer(address, msg -> {
            action.apply(msg.body()).onComplete(res -> {
                if (res.succeeded()) {
                    msg.reply(res.result());
                } else {
                    msg.fail(500, "Failed to get response: " + res.cause().getMessage());
                }
            });
        });
    }
}
