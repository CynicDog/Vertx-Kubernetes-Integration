package cynicdog.io.util;

import io.vertx.ext.web.RoutingContext;
import org.infinispan.Cache;

public class EmbeddingUtils {

    public static String retrieveRelevantData(float[] embeddings, Cache<String, float[]> collection) {
        String closestKey = null;
        double maxSimilarity = -1;

        for (String cacheKey : collection.keySet()) {
            float[] cachedEmbeddings = collection.get(cacheKey);

            double similarity = calculateCosineSimilarity(embeddings, cachedEmbeddings);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                closestKey = cacheKey;
            }
        }

        // TODO: Get the document data associated with the embedding key
        // Data associated with key: 451439790 ..
        return closestKey != null ? "Data associated with key: " + closestKey : "No relevant data found.";
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
