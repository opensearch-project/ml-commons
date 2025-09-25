/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.text.StringEscapeUtils;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

/**
 * Utility class for building memory search queries
 */
@Log4j2
@UtilityClass
public class MemorySearchQueryBuilder {

    /**
     * Builds a neural search query for dense embeddings
     *
     * @param queryText The text to search for
     * @param embeddingModelId The embedding model ID
     * @return XContentBuilder with the neural query
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildNeuralQuery(String queryText, String embeddingModelId) throws IOException {
        return jsonXContent
            .contentBuilder()
            .startObject()
            .startObject("neural")
            .startObject(MEMORY_EMBEDDING_FIELD)
            .field("query_text", queryText)
            .field("model_id", embeddingModelId)
            .endObject()
            .endObject()
            .endObject();
    }

    /**
     * Builds a neural sparse search query for sparse embeddings
     *
     * @param queryText The text to search for
     * @param embeddingModelId The embedding model ID
     * @return XContentBuilder with the neural_sparse query
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildNeuralSparseQuery(String queryText, String embeddingModelId) throws IOException {
        return jsonXContent
            .contentBuilder()
            .startObject()
            .startObject("neural_sparse")
            .startObject(MEMORY_EMBEDDING_FIELD)
            .field("query_text", queryText)
            .field("model_id", embeddingModelId)
            .endObject()
            .endObject()
            .endObject();
    }

    /**
     * Builds a match query for non-semantic search
     *
     * @param queryText The text to search for
     * @return XContentBuilder with the match query
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildMatchQuery(String queryText) throws IOException {
        return jsonXContent.contentBuilder().startObject().startObject("match").field(MEMORY_FIELD, queryText).endObject().endObject();
    }

    /**
     * Builds a query based on the storage configuration
     *
     * @param queryText The text to search for
     * @param memoryConfig The memory storage configuration
     * @return XContentBuilder with the appropriate query
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildQueryByStorageType(String queryText, MemoryConfiguration memoryConfig) throws IOException {
        if (memoryConfig != null) {
            if (memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                return buildNeuralQuery(queryText, memoryConfig.getEmbeddingModelId());
            } else if (memoryConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                return buildNeuralSparseQuery(queryText, memoryConfig.getEmbeddingModelId());
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + memoryConfig.getEmbeddingModelType());
            }
        } else {
            return buildMatchQuery(queryText);
        }
    }

    /**
     * Builds a bool query with filters for searching facts in a session
     *
     * @param strategy The memory strategy containing namespace information
     * @param fact The fact to search for
     * @param namespace The namespace map for filtering
     * @param memoryConfig The memory storage configuration
     * @return QueryBuilder with the bool query
     */
    public static QueryBuilder buildFactSearchQuery(
        MemoryStrategy strategy,
        String fact,
        Map<String, String> namespace,
        MemoryConfiguration memoryConfig
    ) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Add filter conditions
        for (String key : strategy.getNamespace()) {
            if (!namespace.containsKey(key)) {
                throw new IllegalArgumentException("Namespace does not contain key: " + key);
            }
            boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_FIELD + "." + key, namespace.get(key)));
        }
        boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_SIZE_FIELD, strategy.getNamespace().size()));
        boolQuery.filter(QueryBuilders.termQuery(MEMORY_TYPE_FIELD, MemoryStrategyType.SEMANTIC.getValue()));

        // Add the search query
        if (memoryConfig != null) {
            if (memoryConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                StringBuilder neuralSearchQuery = new StringBuilder()
                    .append("{\"neural\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(memoryConfig.getEmbeddingModelId())
                    .append("\"}}}");
                boolQuery.must(QueryBuilders.wrapperQuery(neuralSearchQuery.toString()));
            } else if (memoryConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                StringBuilder neuralSparseQuery = new StringBuilder()
                    .append("{\"neural_sparse\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(memoryConfig.getEmbeddingModelId())
                    .append("\"}}}");
                boolQuery.must(QueryBuilders.wrapperQuery(neuralSparseQuery.toString()));
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + memoryConfig.getEmbeddingModelType());
            }
        } else {
            boolQuery.must(QueryBuilders.matchQuery(MEMORY_FIELD, fact));
        }

        return boolQuery;
    }
}
