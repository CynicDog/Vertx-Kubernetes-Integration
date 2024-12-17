package cynicdog.io.util;

import cynicdog.io.api.OllamaAPI;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import org.infinispan.Cache;

import java.util.List;

public class VectorUtils {

    private final static Logger logger = LoggerFactory.getLogger(VectorUtils.class);

    public static String retrieveRelevantDocument(List<Float> embeddings, Cache<String, OllamaAPI.Embedding> collection) {
        String closestKey = null;
        double maxSimilarity = -1;

        for (String cacheKey : collection.keySet()) {
            List<Float> cachedEmbeddings = collection.get(cacheKey).getLatentScores();

            double similarity = calculateCosineSimilarity(embeddings, cachedEmbeddings);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                closestKey = cacheKey;
            }
        }

        logger.info(String.format("Retrieved similarity: %f", maxSimilarity));

        return closestKey != null ? collection.get(closestKey).getDocument() : "No relevant data found.";
    }

    public static double calculateCosineSimilarity(List<Float> vectorA, List<Float> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("Vectors must be of the same size for cosine similarity calculation.");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            float valueA = vectorA.get(i);
            float valueB = vectorB.get(i);

            dotProduct += valueA * valueB;
            normA += Math.pow(valueA, 2);
            normB += Math.pow(valueB, 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
