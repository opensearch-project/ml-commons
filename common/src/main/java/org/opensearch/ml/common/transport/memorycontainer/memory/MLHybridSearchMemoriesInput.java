/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.index.query.QueryBuilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Input data for hybrid search on long-term memories.
 * Supports optional per-request BM25/neural weight tuning (default 0.5/0.5).
 */
@Getter
@Setter
public class MLHybridSearchMemoriesInput extends MLSearchMemoriesBaseInput {

    public static final float DEFAULT_BM25_WEIGHT = 0.5f;
    public static final float DEFAULT_NEURAL_WEIGHT = 0.5f;

    /** Weight for BM25 keyword search component (0.0–1.0). Must sum to 1.0 with neuralWeight. */
    private float bm25Weight;
    /** Weight for neural/vector search component (0.0–1.0). Must sum to 1.0 with bm25Weight. */
    private float neuralWeight;

    public MLHybridSearchMemoriesInput(
        String memoryContainerId,
        String query,
        int k,
        Map<String, String> namespace,
        Map<String, String> tags,
        Float minScore,
        QueryBuilder filter,
        float bm25Weight,
        float neuralWeight
    ) {
        super(memoryContainerId, query, k, namespace, tags, minScore, filter);
        validateWeights(bm25Weight, neuralWeight);
        this.bm25Weight = bm25Weight;
        this.neuralWeight = neuralWeight;
    }

    public MLHybridSearchMemoriesInput(StreamInput in) throws IOException {
        super(in);
        Float b = in.readOptionalFloat();
        Float n = in.readOptionalFloat();
        this.bm25Weight = b != null ? b : DEFAULT_BM25_WEIGHT;
        this.neuralWeight = n != null ? n : DEFAULT_NEURAL_WEIGHT;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeOptionalFloat(bm25Weight);
        out.writeOptionalFloat(neuralWeight);
    }

    private static void validateWeights(float bm25Weight, float neuralWeight) {
        if (bm25Weight < 0 || bm25Weight > 1 || neuralWeight < 0 || neuralWeight > 1) {
            throw new IllegalArgumentException("bm25_weight and neural_weight must be between 0.0 and 1.0");
        }
        float sum = bm25Weight + neuralWeight;
        if (Math.abs(sum - 1.0f) > 0.001f) {
            throw new IllegalArgumentException("bm25_weight and neural_weight must sum to 1.0, got " + sum);
        }
    }

    public static MLHybridSearchMemoriesInputBuilder builder() {
        return new MLHybridSearchMemoriesInputBuilder();
    }

    public static class MLHybridSearchMemoriesInputBuilder {
        private String memoryContainerId;
        private String query;
        private int k = 10;
        private Map<String, String> namespace;
        private Map<String, String> tags;
        private Float minScore;
        private QueryBuilder filter;
        private float bm25Weight = DEFAULT_BM25_WEIGHT;
        private float neuralWeight = DEFAULT_NEURAL_WEIGHT;

        public MLHybridSearchMemoriesInputBuilder memoryContainerId(String memoryContainerId) {
            this.memoryContainerId = memoryContainerId;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder query(String query) {
            this.query = query;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder k(int k) {
            this.k = k;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder namespace(Map<String, String> namespace) {
            this.namespace = namespace;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder tags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder minScore(Float minScore) {
            this.minScore = minScore;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder filter(QueryBuilder filter) {
            this.filter = filter;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder bm25Weight(float bm25Weight) {
            this.bm25Weight = bm25Weight;
            return this;
        }

        public MLHybridSearchMemoriesInputBuilder neuralWeight(float neuralWeight) {
            this.neuralWeight = neuralWeight;
            return this;
        }

        public MLHybridSearchMemoriesInput build() {
            return new MLHybridSearchMemoriesInput(
                memoryContainerId,
                query,
                k,
                namespace,
                tags,
                minScore,
                filter,
                bm25Weight,
                neuralWeight
            );
        }
    }
}
