package cynicdog.io.util;

import cynicdog.io.api.OllamaAPI;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.infinispan.Cache;

public class VectorUtils {

    private final static Logger logger = LoggerFactory.getLogger(VectorUtils.class);

    public static String retrieveRelevantDocument(float[] embeddings, Cache<String, OllamaAPI.Embedding> collection) {
        String closestKey = null;
        double maxSimilarity = -1;

        for (String cacheKey : collection.keySet()) {
            float[] cachedEmbeddings = collection.get(cacheKey).getLatentScores();

            double similarity = calculateCosineSimilarity(embeddings, cachedEmbeddings);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                closestKey = cacheKey;
            }
        }

        logger.info(String.format("Retrieved similarity: %f", maxSimilarity));

        return closestKey != null ? collection.get(closestKey).getDocument() : "No relevant data found.";
    }

    public static double calculateCosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
