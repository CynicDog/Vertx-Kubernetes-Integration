
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
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
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

    private DefaultCacheManager cacheManager;
    private Configuration cacheConfig;

    @Override
    public void start() throws Exception {

        var ollamaAPI = new OllamaAPI(vertx, OLLAMA_HOST, OLLAMA_PORT, cacheManager, cacheConfig);

        // Register router handlers
        Router router = Router.router(vertx);

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false))));

        // Register EventBus consumers
        registerEventBusConsumer("embed", ollamaAPI::embed);
        registerEventBusConsumer("evict", ollamaAPI::evict);
        registerEventBusConsumer("evictAll", ollamaAPI::evictAll);
        registerEventBusConsumer("generate", ollamaAPI::generate);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    public static void main(String[] args) {

        // Configure default cache manager
        DefaultCacheManager cacheManager = new DefaultCacheManager(
                new GlobalConfigurationBuilder()
                        .transport()
                        .defaultTransport()
                        .addProperty("configurationFile", "default-configs/default-jgroups-kubernetes.xml")
                        .build()
        );

        // Configure Infinispan caches for Vert.x clustering
        cacheManager.defineConfiguration("distributed-cache", new ConfigurationBuilder().clustering().cacheMode(CacheMode.DIST_SYNC).build());
        cacheManager.defineConfiguration("__vertx.subs", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.haInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.nodeInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());

        ClusterManager clusterManager = new InfinispanClusterManager(cacheManager);

        // Configure the cache for embeddings
        Configuration cacheConfig = new ConfigurationBuilder().clustering()
                .cacheMode(CacheMode.REPL_SYNC)
                .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .encoding().value().mediaType(MediaType.APPLICATION_PROTOSTREAM_TYPE)
                .build();

        // Create an instance of Main and set the cache manager and config
        Main mainVerticle = new Main();
        mainVerticle.cacheManager = cacheManager;
        mainVerticle.cacheConfig = cacheConfig;

        // Deploy the verticle
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(mainVerticle))
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

