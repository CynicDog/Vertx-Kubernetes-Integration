package cynicdog.io.api;

import cynicdog.io.data.Embedding;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.infinispan.Cache;
import org.infinispan.commons.api.CacheContainerAdmin;
import org.infinispan.commons.dataconversion.MediaType;
import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import java.util.List;

import static cynicdog.io.Main.POD_NAME;
import static cynicdog.io.util.VectorUtils.retrieveRelevantDocument;

public class OllamaAPI {

    static final Logger logger = LoggerFactory.getLogger(OllamaAPI.class);

    final String host;
    final int port;

    Configuration cacheConfig;

    public OllamaAPI(WebClient client, String host, int port) {
        this.host = host;
        this.port = port;

        // Configure the cache for embeddings
        this.cacheConfig = new ConfigurationBuilder().clustering()
                .cacheMode(CacheMode.REPL_SYNC)
                .encoding().key().mediaType(MediaType.APPLICATION_OBJECT_TYPE)
                .encoding().value().mediaType(MediaType.APPLICATION_PROTOSTREAM_TYPE)
                .build();

        String[] models = {"mxbai-embed-large:latest", "qwen:1.8b"};

        for (String model : models) {
            client.post(port, host, "/api/pull")
                    .sendJsonObject(new JsonObject().put("model", model))
                    .onSuccess(res -> logger.info("Model " + model + " pulled."))
                    .onFailure(err -> logger.error("Embedding request failed: ", err));
        }
    }

    public Future<String> embed(WebClient client, String prompt, DefaultCacheManager cacheManager) {

        Promise<String> promise = Promise.promise();

        if (cacheManager.cacheExists("embeddings")) {
            logger.info(String.format("Cache %s exists with the hashcode of %d on %s node.",
                    "embeddings",
                    cacheManager.getCache("embeddings").hashCode(),
                    cacheManager.getNodeAddress())
            );
        } else {
            logger.info(String.format("Cache %s does not exist, a new cache is created on %s node.",
                    "embeddings",
                    cacheManager.getNodeAddress()
            ));
        }

        Cache<String, Embedding> collection = cacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("embeddings", cacheConfig);

        client.post(port, host, "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {
                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");
                    List<Float> latentScores = embeddingsJson.stream()
                            .map(value -> ((Number) value).floatValue())
                            .toList();

                    String key = Integer.toString(prompt.hashCode());

                    // Store the embeddings in the cache
                    collection.put(key, new Embedding(latentScores, prompt));

                    var message = String.format("Embedding entry stored with key: %s \nFrom: %s (Collection Size: %d)", key, POD_NAME, collection.size());
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

    public Future<String> evict(WebClient client, String key, DefaultCacheManager cacheManager) {

        Promise<String> promise = Promise.promise();

        if (cacheManager.cacheExists("embeddings")) {
            logger.info(String.format("Cache %s exists with the hashcode of %d on %s node.",
                    "embeddings",
                    cacheManager.getCache("embeddings").hashCode(),
                    cacheManager.getNodeAddress())
            );
        } else {
            logger.info(String.format("Cache %s does not exist, a new cache is created on %s node.",
                    "embeddings",
                    cacheManager.getNodeAddress()
            ));
        }

        Cache<String, Embedding> collection = cacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("embeddings", cacheConfig);

        try {
            collection.evict(key);

            var message = String.format("Embedding entry evicted with key: %s \nFrom: %s (Collection Size: %d)", key, POD_NAME, collection.size());
            logger.info(message);
            promise.complete(message);
        } catch (Exception e) {
            logger.error(e.getMessage());
            promise.fail(e.getMessage());
        }
        return promise.future();
    }

    public Future<String> evictAll(WebClient client, String keys, DefaultCacheManager cacheManager) {

        Promise<String> promise = Promise.promise();

        if (cacheManager.cacheExists("embeddings")) {
            logger.info(String.format("Cache %s exists with the hashcode of %d on %s node.",
                    "embeddings",
                    cacheManager.getCache("embeddings").hashCode(),
                    cacheManager.getNodeAddress())
            );
        } else {
            logger.info(String.format("Cache %s does not exist, a new cache is created on %s node.",
                    "embeddings",
                    cacheManager.getNodeAddress()
            ));
        }

        Cache<String, Embedding> collection = cacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("embeddings", cacheConfig);

        try {
            for (String cacheKey : collection.keySet()) {
                collection.evict(cacheKey);
            }
            var message = String.format("All embedding entries evicted \nFrom: %s (Collection Size: %d)", POD_NAME, collection.size());
            logger.info(message);
            promise.complete(message);
        } catch (Exception e) {
            logger.error(e.getMessage());
            promise.fail(e.getMessage());
        }
        return promise.future();
    }

    public Future<String> generate(WebClient client, String prompt, DefaultCacheManager cacheManager) {

        Promise<String> promise = Promise.promise();

        if (cacheManager.cacheExists("embeddings")) {
            logger.info(String.format("Cache %s exists with the hashcode of %d on %s node.",
                    "embeddings",
                    cacheManager.getCache("embeddings").hashCode(),
                    cacheManager.getNodeAddress())
            );
        } else {
            logger.info(String.format("Cache %s does not exist, a new cache is created on %s node.",
                    "embeddings",
                    cacheManager.getNodeAddress()
            ));
        }

        Cache<String, Embedding> collection = cacheManager
                .administration()
                .withFlags(CacheContainerAdmin.AdminFlag.VOLATILE)
                .getOrCreateCache("embeddings", cacheConfig);

        client.post(port, host, "/api/embeddings")
                .sendJsonObject(new JsonObject()
                        .put("model", "mxbai-embed-large:latest")
                        .put("prompt", prompt))
                .onSuccess(res -> {
                    JsonArray embeddingsJson = res.bodyAsJsonObject().getJsonArray("embedding");
                    List<Float> embeddings = embeddingsJson.stream()
                            .map(value -> ((Number) value).floatValue())
                            .toList();

                    String document = retrieveRelevantDocument(embeddings, collection);

                    client.post(port, host, "/api/generate")
                            .sendJsonObject(new JsonObject()
                                    .put("model", "qwen:1.8b")
                                    .put("prompt", String.format("Using this data: %s, respond to this prompt: %s", document, prompt))
                                    .put("stream", false))
                            .onSuccess(success -> {
                                var response = success.bodyAsJsonObject().getString("response");
                                response += String.format("\n\nReferenced document: %s. \nFrom: %s (Collection Size: %d)", document, POD_NAME, collection.size());
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
}
