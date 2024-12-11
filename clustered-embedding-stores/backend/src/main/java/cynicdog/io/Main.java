package cynicdog.io;

import io.vertx.core.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.ext.cluster.infinispan.ClusterHealthCheck;
import io.vertx.ext.cluster.infinispan.InfinispanClusterManager;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.web.Router;
import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;

import java.util.Arrays;

public class Main extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    private static final int HTTP_PORT = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
    private static final String POD_NAME = System.getenv().getOrDefault("POD_NAME", "unknown");

    private static DefaultCacheManager cacheManager;
    private static ClusterManager clusterManager;
    private static Cache<String, float[]> embeddingsCache;  // Cache for storing embeddings

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
        cacheManager.createCache("embeddings", new ConfigurationBuilder().memory().maxSize("1000").build());

        // Initialize the cache for embeddings
        embeddingsCache = cacheManager.getCache("embeddings");

        logger.info("Cluster Manager initialized with shared DefaultCacheManager and embeddings cache.");
    }

    private void registerConsumer() {

        vertx.eventBus().consumer("store-embedding", msg -> {
            try {
                String docId = msg.body().toString();
                float[] embedding = generateEmbeddingForText(docId);

                embeddingsCache.put(docId, embedding);

                msg.reply("Embedding stored successfully for " + docId);
            } catch (Exception e) {
                msg.fail(500, "Failed to store embedding: " + e.getMessage());
            }
        });

        vertx.eventBus().consumer("retrieve-embedding", msg -> {
            String docId = msg.body().toString();
            float[] embedding = embeddingsCache.get(docId);

            if (embedding != null) {
                msg.reply(Arrays.toString(embedding));  // Reply with embedding as a string
            } else {
                msg.reply("No embedding found for " + docId);
            }
        });

        vertx.eventBus().<String>consumer("greetings", msg -> {
            msg.reply(String.format("Hello %s from %s", msg.body(), POD_NAME));
        });
    }

    private void setupRouter(Router router) {
        router.get("/health").handler(context -> context.response().end("OK"));

        HealthChecks checks = HealthChecks
                .create(vertx)
                .register("cluster-health", ClusterHealthCheck.createProcedure(vertx, false));

        router.get("/readiness").handler(HealthCheckHandler.createWithHealthChecks(checks));
    }

    private float[] generateEmbeddingForText(String text) {
        // Example: a simple dummy function that generates an embedding (replace with LLM generation logic)
        float[] embedding = new float[10];
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = text.hashCode() % 10 + i;
        }
        return embedding;
    }

    public static void main(String[] args) {
        Vertx.clusteredVertx(new VertxOptions().setClusterManager(clusterManager))
                .compose(v -> v.deployVerticle(new Main()))
                .onFailure(Throwable::printStackTrace);
    }
}
