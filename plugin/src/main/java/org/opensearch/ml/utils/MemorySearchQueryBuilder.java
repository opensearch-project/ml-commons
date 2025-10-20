/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.NAMESPACE_SIZE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.STRATEGY_ID_FIELD;

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
import org.opensearch.ml.common.memorycontainer.RemoteStore;

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
     * @param ownerId The owner ID for filtering
     * @param memoryConfig The memory storage configuration
     * @param memoryContainerId The memory container ID to filter by (prevents cross-container access)
     * @return QueryBuilder with the bool query
     */
    public static QueryBuilder buildFactSearchQuery(
        MemoryStrategy strategy,
        String fact,
        Map<String, String> namespace,
        String ownerId,
        MemoryConfiguration memoryConfig,
        String memoryContainerId
    ) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // Add filter conditions
        for (String key : strategy.getNamespace()) {
            if (!namespace.containsKey(key)) {
                throw new IllegalArgumentException("Namespace does not contain key: " + key);
            }
            boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_FIELD + "." + key, namespace.get(key)));
        }
        if (ownerId != null) {
            boolQuery.filter(QueryBuilders.termQuery(OWNER_ID_FIELD, ownerId));
        }
        boolQuery.filter(QueryBuilders.termQuery(NAMESPACE_SIZE_FIELD, strategy.getNamespace().size()));
        // Filter by strategy_id to prevent cross-strategy interference (sufficient for uniqueness)
        boolQuery.filter(QueryBuilders.termQuery(STRATEGY_ID_FIELD, strategy.getId()));
        // Filter by memory_container_id to prevent cross-container access when containers share the same index prefix
        if (memoryContainerId != null && !memoryContainerId.isBlank()) {
            boolQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, memoryContainerId));
        }

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

    /**
     * Builds a fact search query for AOSS with neural search support
     * Similar to buildFactSearchQuery but returns a JSON string for remote execution
     *
     * @param strategy The memory strategy containing namespace information
     * @param fact The fact to search for
     * @param namespace The namespace map for filtering
     * @param ownerId The owner ID for filtering
     * @param memoryConfig The memory storage configuration
     * @param memoryContainerId The memory container ID to filter by
     * @param maxInferSize Maximum number of results to return
     * @return JSON string with the search query
     */
    public static String buildFactSearchQueryForAoss(
        MemoryStrategy strategy,
        String fact,
        Map<String, String> namespace,
        String ownerId,
        MemoryConfiguration memoryConfig,
        String memoryContainerId,
        int maxInferSize
    ) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("{\"size\":").append(maxInferSize).append(",\"query\":{\"bool\":{\"filter\":[");

        // Add filter conditions
        boolean firstFilter = true;
        for (String key : strategy.getNamespace()) {
            if (!namespace.containsKey(key)) {
                throw new IllegalArgumentException("Namespace does not contain key: " + key);
            }
            if (!firstFilter) {
                queryBuilder.append(",");
            }
            queryBuilder
                .append("{\"term\":{\"")
                .append(NAMESPACE_FIELD)
                .append(".")
                .append(key)
                .append("\":\"")
                .append(StringEscapeUtils.escapeJson(namespace.get(key)))
                .append("\"}}");
            firstFilter = false;
        }

        if (ownerId != null) {
            if (!firstFilter) {
                queryBuilder.append(",");
            }
            queryBuilder
                .append("{\"term\":{\"")
                .append(OWNER_ID_FIELD)
                .append("\":\"")
                .append(StringEscapeUtils.escapeJson(ownerId))
                .append("\"}}");
            firstFilter = false;
        }

        if (!firstFilter) {
            queryBuilder.append(",");
        }
        queryBuilder.append("{\"term\":{\"").append(NAMESPACE_SIZE_FIELD).append("\":").append(strategy.getNamespace().size()).append("}}");

        // Filter by strategy_id to prevent cross-strategy interference (sufficient for uniqueness)
        queryBuilder
            .append(",{\"term\":{\"")
            .append(STRATEGY_ID_FIELD)
            .append("\":\"")
            .append(StringEscapeUtils.escapeJson(strategy.getId()))
            .append("\"}}");

        // Filter by memory_container_id to prevent cross-container access when containers share the same index prefix
        if (memoryContainerId != null && !memoryContainerId.isBlank()) {
            queryBuilder
                .append(",{\"term\":{\"")
                .append(MEMORY_CONTAINER_ID_FIELD)
                .append("\":\"")
                .append(StringEscapeUtils.escapeJson(memoryContainerId))
                .append("\"}}");
        }

        queryBuilder.append("],\"must\":[");

        RemoteStore remoteStore = memoryConfig.getRemoteStore();
        // Add the search query based on embedding type
        if (remoteStore != null && remoteStore.getEmbeddingModelId() != null) {
            // Determine which embedding model ID to use
            String embeddingModelId = remoteStore.getEmbeddingModelId();

            if (remoteStore.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                // Neural search for dense embeddings
                queryBuilder
                    .append("{\"neural\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(StringEscapeUtils.escapeJson(embeddingModelId))
                    .append("\"}}}");
            } else if (remoteStore.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                // Neural sparse search for sparse embeddings
                queryBuilder
                    .append("{\"neural_sparse\":{\"")
                    .append(MEMORY_EMBEDDING_FIELD)
                    .append("\":{\"query_text\":\"")
                    .append(StringEscapeUtils.escapeJson(fact))
                    .append("\",\"model_id\":\"")
                    .append(StringEscapeUtils.escapeJson(embeddingModelId))
                    .append("\"}}}");
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + memoryConfig.getEmbeddingModelType());
            }
        } else {
            // Fallback to match query if no embedding configured
            queryBuilder
                .append("{\"match\":{\"")
                .append(MEMORY_FIELD)
                .append("\":\"")
                .append(StringEscapeUtils.escapeJson(fact))
                .append("\"}}");
        }

        queryBuilder.append("]}}}");

        return queryBuilder.toString();
    }
}
