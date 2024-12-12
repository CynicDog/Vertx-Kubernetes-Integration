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
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import org.infinispan.Cache;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.StorageType;
import org.infinispan.manager.DefaultCacheManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    private static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";

    private static DefaultCacheManager cacheManager;
    private static ClusterManager clusterManager;

    @Override
    public void start() throws Exception {
        setupClusterManager();
        registerConsumer();

        Router router = Router.router(vertx);
        setupRouter(router);

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(HTTP_PORT)
                .onSuccess(server -> logger.info("HTTP server started on port " + server.actualPort()));
    }

    private void setupClusterManager() {
        cacheManager = new DefaultCacheManager();
        clusterManager = new InfinispanClusterManager(cacheManager);

        // Configure the cache for embeddings
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.encoding()
                .mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .memory()
                .storageType(StorageType.BINARY)
                .maxSize("1000");

        cacheManager.createCache("embeddings", builder.build());

        logger.info("Cluster Manager initialized with shared DefaultCacheManager and embeddings cache.");
    }

    private void registerConsumer() {

        vertx.eventBus().consumer("store-embedding", msg -> {
            try {
                String docId = msg.body().toString();
                float[] embedding = generateEmbeddingForText(docId);

                cacheManager.getCache("embeddings").put(docId, embedding);

                msg.reply("Embedding stored successfully for " + docId);
            } catch (Exception e) {
                msg.fail(500, "Failed to store embedding: " + e.getMessage());
            }
        });

        vertx.eventBus().consumer("retrieve-embedding", msg -> {
            String docId = msg.body().toString();

            float[] embedding = (float[]) cacheManager.getCache("embeddings").get(docId);

            if (embedding != null) {
                msg.reply(String.format("From %s\n%s", POD_NAME, Arrays.toString(embedding)));
            } else {
                msg.reply("No embedding found for " + docId);
            }
        });

        vertx.eventBus().<String>consumer("greetings", msg -> {
            // Call Ollama to get the response
            sendOllamaRequest(msg.body().toString()).onComplete(res -> {
                if (res.succeeded()) {
                    String ollamaResponse = res.result();
                    msg.reply(String.format("Hello %s from %s.\nOllama says: %s", msg.body(), POD_NAME, ollamaResponse));
                } else {
                    msg.fail(500, "Failed to get response from Ollama: " + res.cause().getMessage());
                }
            });
        });
    }

    private void setupRouter(Router router) {
        router.get("/health").handler(context -> context.response().end("OK"));

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false));

        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));
    }

    // TODO: Embedding model integration
    private float[] generateEmbeddingForText(String text) {
        // Example: a simple dummy function that generates an embedding (replace with LLM generation logic)
        float[] embedding = new float[10];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = text.hashCode() % 10 + i;
        }
        return embedding;
    }

    private Future<String> sendOllamaRequest(String name) {

        Promise<String> promise = Promise.promise();
        WebClient client = WebClient.create(vertx, new WebClientOptions().setDefaultPort(11434).setDefaultHost("localhost"));

        List<String> res = new ArrayList<>();
        JsonParser parser = JsonParser.newParser().objectValueMode();
        parser.handler(event -> {
            JsonObject object = event.objectValue();
            res.add(object.encode());
        });

        var body = new JsonObject()
                .put("model", "qwen:1.8b")
                .put("prompt", "Why is the sky blue?");

        // http --stream POST :11434/api/generate model="qwen2.5:3b" prompt="Why is the sky blue?"
        client.post(11434, "localhost", "/api/generate")
                .as(BodyCodec.jsonStream(parser))
                .sendJsonObject(body)
                .onSuccess(response -> {
                    promise.complete(res.toString());
                })
                .onFailure(err -> {
                    logger.error(err);
                    promise.fail(err);
                });

        return promise.future();
    }

    public static void main(String[] args) {
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}