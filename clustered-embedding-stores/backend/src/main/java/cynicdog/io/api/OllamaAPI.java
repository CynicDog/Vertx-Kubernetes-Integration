package cynicdog.io.api;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.infinispan.Cache;

import static cynicdog.io.Main.POD_NAME;
import static cynicdog.io.util.VectorUtils.retrieveRelevantDocument;

public class OllamaAPI {

    static final Logger logger = LoggerFactory.getLogger(OllamaAPI.class);

    final String host;
    final int port;

    final Vertx vertx;
    final WebClient client;
    final Cache<String, Embedding> collection;

    public OllamaAPI(Vertx vertx, String host, int port, Cache<String, Embedding> collection) {
        this.host = host;
        this.port = port;
        this.vertx = vertx;
        this.client = WebClient.create(vertx);
        this.collection = collection;

        String[] models = {"mxbai-embed-large:latest", "qwen:1.8b"};

        for (String model : models) {
            client.post(port, host, "/api/pull")
                    .sendJsonObject(new JsonObject().put("model", model))
                    .onSuccess(res -> logger.info("Model " + model + " pulled."))
                    .onFailure(err -> logger.error("Embedding request failed: ", err));
        }
    }

    public Future<String> embed(String prompt) {

        Promise<String> promise = Promise.promise();

        client.post(port, host, "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {
                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");
                    float[] latentScores = new float[embeddingsJson.size()];
                    for (int i = 0; i < embeddingsJson.size(); i++) {
                        latentScores[i] = embeddingsJson.getFloat(i);
                    }
                    String key = Integer.toString(prompt.hashCode());

                    // Store the embeddings in the cache
                    collection.put(key, new Embedding(latentScores, prompt));

                    var message = "Embedding entry stored with key: " + key;
                    logger.info(message);
                    promise.complete(message);
                })
                .onFailure(err -> {
                    var message = "Embedding request failed: " + err.getMessage();
                    logger.error(message);
                    promise.fail(message);
                });

        return promise.future();
    }

    public Future<String> generate(String prompt) {

        Promise<String> promise = Promise.promise();

        client.post(port, host, "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {
                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");
                    float[] embeddings = new float[embeddingsJson.size()];
                    for (int i = 0; i < embeddingsJson.size(); i++) {
                        embeddings[i] = embeddingsJson.getFloat(i);
                    }
                    String document = retrieveRelevantDocument(embeddings, collection);

                    client.post(11434, "localhost", "/api/generate")
                            .sendJsonObject(new JsonObject()
                                    .put("model", "qwen:1.8b")
                                    .put("prompt", String.format("Using this data: %s, respond to this prompt: %s", document, prompt))
                                    .put("stream", false))
                            .onSuccess(success -> {
                                var response = success.bodyAsJsonObject().getString("response");
                                response += String.format("\n\nFrom: %s.\nReferenced document: %s.", POD_NAME, document);

                                promise.complete(response);
                            })
                            .onFailure(err -> {
                                logger.error("Failed to connect to Ollama", err);
                                promise.fail(err);
                            });
                })
                .onFailure(err -> {
                    logger.error("Generate request failed: " + err.getMessage());
                    promise.fail(err);
                });

        return promise.future();
    }

    public static class Embedding {
        private float[] latentScores;
        private String document;

        public Embedding(float[] latentScores, String document) {
            this.latentScores = latentScores;
            this.document = document;
        }

        public float[] getLatentScores() {
            return latentScores;
        }

        public void setLatentScores(float[] latentScores) {
            this.latentScores = latentScores;
        }

        public String getDocument() {
            return document;
        }

        public void setDocument(String document) {
            this.document = document;
        }
    }
}
