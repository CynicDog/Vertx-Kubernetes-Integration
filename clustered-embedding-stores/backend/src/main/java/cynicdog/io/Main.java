
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
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;


public class Main extends AbstractVerticle {

    private static final String OLLAMA_HOST = System.getenv().getOrDefault("OLLAMA_HOST", "localhost");
    private static final int OLLAMA_PORT = Integer.parseInt(System.getenv().getOrDefault("OLLAMA_PORT", "11434"));
    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    public static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    static DefaultCacheManager cacheManager;

    @Override
    public void start() throws Exception {

        // Register router handlers
        Router router = Router.router(vertx);
        WebClient client = WebClient.create(vertx);

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false))));

        var ollamaAPI = new OllamaAPI(WebClient.create(vertx), OLLAMA_HOST, OLLAMA_PORT);

        // Register consumers
        vertx.eventBus().<String>consumer("embed", msg -> {
            ollamaAPI.embed(client, msg.body(), cacheManager)
                    .onComplete(res -> {
                        if (res.succeeded()) {
                            msg.reply(res.result());
                        } else {
                            msg.fail(500, "Failed to get response: " + res.cause().getMessage());;
                        }
                    });
        });
        vertx.eventBus().<String>consumer("evict", msg -> {
            ollamaAPI.evict(client, msg.body(), cacheManager)
                    .onComplete(res -> {
                        if (res.succeeded()) {
                            msg.reply(res.result());
                        } else {
                            msg.fail(500, "Failed to get response: " + res.cause().getMessage());;
                        }
                    });
        });
        vertx.eventBus().<String>consumer("evictAll", msg -> {
            ollamaAPI.evictAll(client, msg.body(), cacheManager)
                    .onComplete(res -> {
                        if (res.succeeded()) {
                            msg.reply(res.result());
                        } else {
                            msg.fail(500, "Failed to get response: " + res.cause().getMessage());;
                        }
                    });
        });
        vertx.eventBus().<String>consumer("generate", msg -> {
            ollamaAPI.generate(client, msg.body(), cacheManager)
                    .onComplete(res -> {
                        if (res.succeeded()) {
                            msg.reply(res.result());
                        } else {
                            msg.fail(500, "Failed to get response: " + res.cause().getMessage());;
                        }
                    });
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    public static void main(String[] args) {

        var globalConfig = new GlobalConfigurationBuilder()
                .transport()
                .defaultTransport()
                .build();

        // Configure default cache manager
        cacheManager = new DefaultCacheManager(globalConfig);

        // Configure Infinispan caches for Vert.x clustering
        cacheManager.defineConfiguration("__vertx.subs", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.haInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.nodeInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());

        ClusterManager clusterManager = new InfinispanClusterManager(cacheManager);

        // Deploy the verticle
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}

