package cynicdog.io;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.global.GlobalConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));

    @Override
    public void start() throws Exception {

        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false))));

        router.post("/evict").handler(context -> handleRequest(context, "evict"));
        router.post("/evictAll").handler(context -> handleRequest(context, "evictAll"));
        router.post("/embed").handler(context -> handleRequest(context, "embed"));
        router.post("/generate").handler(context -> handleRequest(context, "generate"));

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
        cacheManager.defineConfiguration("__vertx.subs", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.haInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());
        cacheManager.defineConfiguration("__vertx.nodeInfo", new ConfigurationBuilder().clustering().cacheMode(CacheMode.REPL_SYNC).build());

        ClusterManager clusterManager = new InfinispanClusterManager(cacheManager);

        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }

    private void handleRequest(RoutingContext context, String address) {

        String prompt = context.getBodyAsJson().getString("prompt");

        // Set response timeout from embedding server to 40 seconds
        vertx.eventBus().<String>request(address, prompt, new DeliveryOptions().setSendTimeout(40000))
                .map(Message::body)
                .onSuccess(reply -> context.response().end(reply))
                .onFailure(context::fail);
    }
}