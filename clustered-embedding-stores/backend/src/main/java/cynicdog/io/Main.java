package cynicdog.io;

import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.handler.BodyHandler;
import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import static cynicdog.io.util.EmbeddingUtils.retrieveRelevantData;


public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    private static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    private static DefaultCacheManager cacheManager;
    private static ClusterManager clusterManager;

    private static Cache<String, float[]> embeddingsCache;

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

        pullOllamaModels();

        Router router = Router.router(vertx);
        setupRouter(router);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    private void pullOllamaModels() {
        String[] models = {"mxbai-embed-large:latest", "qwen:1.8b"};

        for (String model : models) {
            client.post(11434, "localhost", "/api/pull")
                    .sendJsonObject(new JsonObject().put("model", model))
                    .onSuccess(res -> logger.info("Model " + model + " pulled."))
                    .onFailure(err -> logger.error("Embedding request failed: ", err));
        }
    }

    private void setupRouter(Router router) {

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false));

        router.route().handler(BodyHandler.create());

        router.get("/health").handler(context -> context.response().end("OK"));
        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));

        router.post("/embed").handler(this::handleEmbedRequest);
        router.post("/generate").handler(this::handleGenerateRequest);
    }

    // TODO: handle multiple sentences
    private void handleEmbedRequest(RoutingContext context) {
        String prompt = context.getBodyAsJson().getString("prompt");
        client.post(11434, "localhost", "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {

                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");

                    float[] embeddings = new float[embeddingsJson.size()];
                    for (int i = 0; i < embeddingsJson.size(); i++) {
                        embeddings[i] = embeddingsJson.getFloat(i);
                    }

                    String key = Integer.toString(prompt.hashCode());

                    // Store the embeddings in the cache
                    embeddingsCache.put(key, embeddings);

                    logger.info("Embedding entry stored with key: " + key);
                    context.response().end();
                })
                .onFailure(err -> {
                    logger.error("Embedding request failed: ", err);
                    context.response().setStatusCode(500).end("Failed to generate embeddings.");
                });
    }

    private void handleGenerateRequest(RoutingContext context) {
        String prompt = context.getBodyAsJson().getString("prompt");
        client.post(11434, "localhost", "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {
                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");
                    float[] embeddings = new float[embeddingsJson.size()];
                    for (int i = 0; i < embeddingsJson.size(); i++) {
                        embeddings[i] = embeddingsJson.getFloat(i);
                    }

                    String data = retrieveRelevantData(embeddings, embeddingsCache);

                    context.response().setChunked(true);
                    JsonParser parser = JsonParser.newParser().objectValueMode();
                    parser.handler(event -> {

                        JsonObject json = event.objectValue();
                        String content = json.getString("response");
                        boolean done = json.getBoolean("done", false);

                        context.response().write(content);

                        if (done) {
                            context.response().end(String.format("\n\nFrom - %s", POD_NAME));
                        }
                    });

                    parser.exceptionHandler(err -> {
                        logger.error("JSON streaming failed", err);
                    });

                    client.post(11434, "localhost", "/api/generate")
                            .as(BodyCodec.jsonStream(parser))
                            .sendJsonObject(new JsonObject()
                                    .put("model", "qwen:1.8b")
                                    .put("prompt", String.format("Using this data: %s, respond to this prompt: %s", data, prompt)))
                            .onFailure(err -> {
                                logger.error("Failed to connect to Ollama", err);
                            });
                })
                .onFailure(err -> {
                    logger.error("Generate request failed: ", err);
                    context.response().setStatusCode(500).end("Failed to generate response.");
                });
    }

    public static void main(String[] args) {
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}