package cynicdog.io;

import com.hazelcast.config.Config;
import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.ClusterHealthCheck;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "0"));
    private static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    @Override
    public void start() throws Exception {

        registerConsumer();

        Router router = Router.router(vertx);
        setupRouter(router);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    private void registerConsumer() {
        vertx.eventBus().<String>consumer("greetings", msg -> {
            msg.reply(String.format("Hello %s from %s", msg.body(), POD_NAME));
        });
    }

    private void setupRouter(Router router) {

        router.get("/health").handler(context -> context.response().end("OK"));

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx));

        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));
    }

    public static void main(String[] args) {

        Config hazelcastConfig = new Config();

        hazelcastConfig.getNetworkConfig()
                .getJoin()
                .getMulticastConfig()
                .setEnabled(false);

        hazelcastConfig
                .getNetworkConfig()
                .getJoin()
                .getTcpIpConfig()
                .setEnabled(false);

        hazelcastConfig
                .getNetworkConfig()
                .getJoin()
                .getKubernetesConfig()
                .setEnabled(true)
                .setProperty("namespace", "default")
                .setProperty("service-name", "clustered-app");

        Vertx.builder()
                .withClusterManager(new HazelcastClusterManager(hazelcastConfig))
                .buildClustered()
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}