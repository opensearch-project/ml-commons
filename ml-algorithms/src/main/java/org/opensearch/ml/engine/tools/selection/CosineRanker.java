/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks tools by exact cosine similarity between a search phrase and each description embedding.
 * Brute force is fine for pools of a few hundred tools. Plain relevance, not MMR: tool descriptions
 * resemble each other more than any query, so a redundancy penalty outweighs relevance. Ties break
 * on name ascending so results are reproducible.
 */
public final class CosineRanker {

    private CosineRanker() {}

    /**
     * @return cosine of the angle between the two vectors, in [-1, 1]; {@code 0.0} when either
     *         vector is null, empty, zero-length in magnitude, or of a different dimension
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * @param queryVector      embedding of the search phrase
     * @param candidateVectors tool name to description embedding. Entries whose vector is null or
     *                         whose dimension differs from the query are skipped.
     * @param topK             maximum number of names to return
     * @return the most similar tool names, best first; empty when nothing usable was supplied
     */
    public static List<String> topK(float[] queryVector, Map<String, float[]> candidateVectors, int topK) {
        if (queryVector == null || candidateVectors == null || candidateVectors.isEmpty() || topK < 1) {
            return List.of();
        }
        Map<String, Double> relevance = new LinkedHashMap<>();
        for (Map.Entry<String, float[]> entry : candidateVectors.entrySet()) {
            float[] vector = entry.getValue();
            if (vector == null || vector.length != queryVector.length) {
                continue;
            }
            double score = cosineSimilarity(queryVector, vector);
            // NaN sorts above every real score, so a corrupt vector would otherwise rank first.
            if (!Double.isFinite(score)) {
                continue;
            }
            relevance.put(entry.getKey(), score);
        }
        List<String> ranked = new ArrayList<>(relevance.keySet());
        ranked.sort(Comparator.<String, Double>comparing(relevance::get).reversed().thenComparing(Comparator.naturalOrder()));
        return List.copyOf(ranked.subList(0, Math.min(topK, ranked.size())));
    }
}
