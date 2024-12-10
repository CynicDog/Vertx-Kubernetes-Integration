package cynicdog.io;

import com.hazelcast.config.Config;
import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.spi.cluster.hazelcast.ClusterHealthCheck;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;

public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));

    @Override
    public void start() throws Exception {

        Router router = Router.router(vertx);
        setupRouter(router);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    private void setupRouter(Router router) {

        router.get("/hello").handler(this::handleHelloRequest);

        router.get("/health").handler(context -> context.response().end("OK"));

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx));

        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));
    }

    private void handleHelloRequest(RoutingContext context) {
        vertx.eventBus().<String>request("greetings", context.queryParams().get("name"))
                .map(Message::body)
                .onSuccess(reply -> context.response().end(reply))
                .onFailure(context::fail);
    }

    public static void main(String[] args) {

        Config hazelcastConfig = new Config();

        hazelcastConfig
                .getNetworkConfig()
                .getJoin()
                .getMulticastConfig()
                .setEnabled(true);

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