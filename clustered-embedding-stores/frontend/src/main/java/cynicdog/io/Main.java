package cynicdog.io;

import io.vertx.core.*;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

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

        // http :8080/hello name=="Vert.x Clustering"
        router.get("/hello").handler(this::handleHelloRequest);

        // http POST :8080/store-embedding docId=="doc1"
        router.post("/store-embedding").handler(this::handleStoreEmbeddingRequest);

        // http :8080/retrieve-embedding docId=="doc1"
        router.get("/retrieve-embedding").handler(this::handleRetrieveEmbeddingRequest);

        router.get("/health").handler(context -> context.response().end("OK"));

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false));

        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));
    }

    private void handleHelloRequest(RoutingContext context) {
        vertx.eventBus().<String>request("greetings", context.queryParams().get("name"))
                .map(Message::body)
                .onSuccess(reply -> context.response().end(reply))
                .onFailure(context::fail);
    }

    private void handleStoreEmbeddingRequest(RoutingContext context) {
        String docId = context.request().getParam("docId");
        if (docId == null || docId.isEmpty()) {
            context.response().setStatusCode(400).end("Document ID is required");
            return;
        }

        vertx.eventBus().<String>request("store-embedding", docId)
                .onSuccess(reply -> context.response().end(reply.body()))
                .onFailure(err -> {
                    logger.error("Failed to store embedding", err);
                    context.response().setStatusCode(500).end("Failed to store embedding");
                });
    }

    private void handleRetrieveEmbeddingRequest(RoutingContext context) {
        String docId = context.request().getParam("docId");
        if (docId == null || docId.isEmpty()) {
            context.response().setStatusCode(400).end("Document ID is required");
            return;
        }

        vertx.eventBus().<String>request("retrieve-embedding", docId)
                .onSuccess(reply -> context.response().end(reply.body()))
                .onFailure(err -> {
                    logger.error("Failed to retrieve embedding", err);
                    context.response().setStatusCode(500).end("Failed to retrieve embedding");
                });
    }

    public static void main(String[] args) {

        Vertx.clusteredVertx(new VertxOptions())
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}