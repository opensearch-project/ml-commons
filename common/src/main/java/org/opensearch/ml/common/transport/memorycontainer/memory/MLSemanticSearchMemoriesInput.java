/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import java.io.IOException;
import java.util.Map;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.index.query.QueryBuilder;

import lombok.Getter;
import lombok.Setter;

/**
 * Input data for semantic search on long-term memories
 */
@Getter
@Setter
public class MLSemanticSearchMemoriesInput extends MLSearchMemoriesBaseInput {

    public MLSemanticSearchMemoriesInput(
        String memoryContainerId,
        String query,
        int k,
        Map<String, String> namespace,
        Map<String, String> tags,
        Float minScore,
        QueryBuilder filter
    ) {
        super(memoryContainerId, query, k, namespace, tags, minScore, filter);
    }

    public MLSemanticSearchMemoriesInput(StreamInput in) throws IOException {
        super(in);
    }

    public static MLSemanticSearchMemoriesInputBuilder builder() {
        return new MLSemanticSearchMemoriesInputBuilder();
    }

    public static class MLSemanticSearchMemoriesInputBuilder {
        private String memoryContainerId;
        private String query;
        private int k = 10;
        private Map<String, String> namespace;
        private Map<String, String> tags;
        private Float minScore;
        private QueryBuilder filter;

        public MLSemanticSearchMemoriesInputBuilder memoryContainerId(String memoryContainerId) {
            this.memoryContainerId = memoryContainerId;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder query(String query) {
            this.query = query;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder k(int k) {
            this.k = k;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder namespace(Map<String, String> namespace) {
            this.namespace = namespace;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder tags(Map<String, String> tags) {
            this.tags = tags;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder minScore(Float minScore) {
            this.minScore = minScore;
            return this;
        }

        public MLSemanticSearchMemoriesInputBuilder filter(QueryBuilder filter) {
            this.filter = filter;
            return this;
        }

        public MLSemanticSearchMemoriesInput build() {
            return new MLSemanticSearchMemoriesInput(memoryContainerId, query, k, namespace, tags, minScore, filter);
        }
    }
}
