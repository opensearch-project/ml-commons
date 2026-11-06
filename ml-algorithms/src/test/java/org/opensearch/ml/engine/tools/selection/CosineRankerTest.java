/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class CosineRankerTest {

    @Test
    public void testCosineOfIdenticalVectorsIsOne() {
        float[] v = new float[] { 1f, 2f, 3f };
        assertEquals(1.0, CosineRanker.cosineSimilarity(v, v), 1e-6);
    }

    @Test
    public void testCosineOfOrthogonalVectorsIsZero() {
        assertEquals(0.0, CosineRanker.cosineSimilarity(new float[] { 1f, 0f }, new float[] { 0f, 1f }), 1e-6);
    }

    @Test
    public void testCosineOfOppositeVectorsIsMinusOne() {
        assertEquals(-1.0, CosineRanker.cosineSimilarity(new float[] { 1f, 0f }, new float[] { -1f, 0f }), 1e-6);
    }

    @Test
    public void testCosineIsScaleInvariant() {
        // Embedding models do not always return unit vectors.
        assertEquals(1.0, CosineRanker.cosineSimilarity(new float[] { 1f, 1f }, new float[] { 5f, 5f }), 1e-6);
    }

    @Test
    public void testCosineDegradesToZeroOnBadInput() {
        assertEquals(0.0, CosineRanker.cosineSimilarity(null, new float[] { 1f }), 0.0);
        assertEquals(0.0, CosineRanker.cosineSimilarity(new float[] { 1f }, null), 0.0);
        assertEquals(0.0, CosineRanker.cosineSimilarity(new float[] { 1f, 2f }, new float[] { 1f }), 0.0);
        assertEquals(0.0, CosineRanker.cosineSimilarity(new float[] { 0f, 0f }, new float[] { 1f, 1f }), 0.0);
        assertEquals(0.0, CosineRanker.cosineSimilarity(new float[0], new float[0]), 0.0);
    }

    @Test
    public void testReturnsMostSimilarFirst() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("d", new float[] { 0f, 1f });          // cos 0.0000
        candidates.put("b", new float[] { 0.95f, 0.31f });    // cos 0.9507
        candidates.put("a", new float[] { 1f, 0f });          // cos 1.0000
        candidates.put("c", new float[] { 0.94f, 0.34f });    // cos 0.9404

        assertEquals(List.of("a", "b", "c"), CosineRanker.topK(query, candidates, 3));
    }

    @Test
    public void testNearDuplicatesAreBothKept() {
        // Similar relevant tools must both survive; no redundancy penalty.
        float[] query = new float[] { 1f, 0f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("search", new float[] { 0.9f, 0.436f, 0f });
        candidates.put("msearch", new float[] { 0.85f, 0.527f, 0f });
        candidates.put("mapping", new float[] { 0.5f, 0f, 0.866f });

        assertEquals(List.of("search", "msearch"), CosineRanker.topK(query, candidates, 2));
    }

    @Test
    public void testTopKCapsTheResult() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        for (int i = 0; i < 20; i++) {
            candidates.put("tool" + i, new float[] { 1f - (i / 100f), i / 100f });
        }
        assertEquals(5, CosineRanker.topK(query, candidates, 5).size());
    }

    @Test
    public void testReturnsEverythingWhenPoolIsSmallerThanTopK() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("a", new float[] { 1f, 0f });
        candidates.put("b", new float[] { 0f, 1f });

        assertEquals(List.of("a", "b"), CosineRanker.topK(query, candidates, 5));
    }

    @Test
    public void testSkipsCandidatesWithMissingOrMismatchedVectors() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("good", new float[] { 1f, 0f });
        candidates.put("null-vector", null);
        candidates.put("wrong-dimension", new float[] { 1f, 0f, 0f });

        assertEquals(List.of("good"), CosineRanker.topK(query, candidates, 5));
    }

    @Test
    public void testEmptyAndNullInputsYieldEmptyResult() {
        assertTrue(CosineRanker.topK(new float[] { 1f, 0f }, Map.of(), 4).isEmpty());
        assertTrue(CosineRanker.topK(new float[] { 1f, 0f }, null, 4).isEmpty());
        assertTrue(CosineRanker.topK(null, Map.of("a", new float[] { 1f }), 4).isEmpty());
        assertTrue(CosineRanker.topK(new float[] { 1f }, Map.of("a", new float[] { 1f }), 0).isEmpty());
    }

    @Test
    public void testNegativelyCorrelatedToolRanksBelowOrthogonal() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("opposite", new float[] { -1f, 0f });
        candidates.put("orthogonal", new float[] { 0f, 1f });

        assertEquals(List.of("orthogonal", "opposite"), CosineRanker.topK(query, candidates, 2));
    }

    @Test
    public void testTiesBreakByNameForDeterminism() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("zebra", new float[] { 1f, 0f });
        candidates.put("alpha", new float[] { 1f, 0f });

        assertEquals(List.of("alpha", "zebra"), CosineRanker.topK(query, candidates, 2));
    }

    @Test
    public void testInputMapIsNotMutated() {
        float[] query = new float[] { 1f, 0f };
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("a", new float[] { 1f, 0f });
        candidates.put("b", new float[] { 0f, 1f });
        candidates.put("c", new float[] { 0.7f, 0.7f });

        CosineRanker.topK(query, candidates, 1);
        assertEquals(3, candidates.size());
    }

    @Test
    public void testNonFiniteScoresAreLeftOutInsteadOfRankedFirst() {
        Map<String, float[]> candidates = new LinkedHashMap<>();
        candidates.put("good", new float[] { 1f, 0f });
        candidates.put("nan", new float[] { Float.NaN, 0f });
        candidates.put("infinite", new float[] { Float.POSITIVE_INFINITY, 1f });

        assertEquals(List.of("good"), CosineRanker.topK(new float[] { 1f, 0f }, candidates, 3));
    }
}
