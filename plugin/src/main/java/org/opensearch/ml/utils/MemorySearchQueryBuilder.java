/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_EMBEDDING_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_TYPE_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.SESSION_ID_FIELD;

import java.io.IOException;

import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;

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
     * @param storageConfig The memory storage configuration
     * @return XContentBuilder with the appropriate query
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildQueryByStorageType(String queryText, MemoryStorageConfig storageConfig) throws IOException {
        if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
            if (storageConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                return buildNeuralQuery(queryText, storageConfig.getEmbeddingModelId());
            } else if (storageConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                return buildNeuralSparseQuery(queryText, storageConfig.getEmbeddingModelId());
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + storageConfig.getEmbeddingModelType());
            }
        } else {
            return buildMatchQuery(queryText);
        }
    }

    /**
     * Builds a bool query with filters for searching facts in a session
     *
     * @param fact The fact to search for
     * @param sessionId The session ID to filter by
     * @param storageConfig The memory storage configuration
     * @return XContentBuilder with the bool query (without query wrapper)
     * @throws IOException if building query fails
     */
    public static XContentBuilder buildFactSearchQuery(String fact, String sessionId, MemoryStorageConfig storageConfig)
        throws IOException {
        // Build the bool query with filters (no "query" wrapper)
        XContentBuilder boolQuery = jsonXContent.contentBuilder();
        boolQuery.startObject();
        boolQuery.startObject("bool");

        // Add filter conditions
        boolQuery.startObject("filter");
        boolQuery.startObject("bool");
        boolQuery.startArray("must");

        // Filter by session ID
        boolQuery.startObject();
        boolQuery.startObject("term");
        boolQuery.field(SESSION_ID_FIELD, sessionId);
        boolQuery.endObject();
        boolQuery.endObject();

        // Filter by memory type FACT
        boolQuery.startObject();
        boolQuery.startObject("term");
        boolQuery.field(MEMORY_TYPE_FIELD, MemoryType.FACT.getValue());
        boolQuery.endObject();
        boolQuery.endObject();

        boolQuery.endArray(); // end must
        boolQuery.endObject(); // end inner bool
        boolQuery.endObject(); // end filter

        // Add the search query to must
        boolQuery.startArray("must");

        // Inline the appropriate query based on storage type
        boolQuery.startObject();
        if (storageConfig != null && storageConfig.isSemanticStorageEnabled()) {
            if (storageConfig.getEmbeddingModelType() == FunctionName.TEXT_EMBEDDING) {
                // Build neural query inline
                boolQuery.startObject("neural");
                boolQuery.startObject(MEMORY_EMBEDDING_FIELD);
                boolQuery.field("query_text", fact);
                boolQuery.field("model_id", storageConfig.getEmbeddingModelId());
                boolQuery.endObject();
                boolQuery.endObject();
            } else if (storageConfig.getEmbeddingModelType() == FunctionName.SPARSE_ENCODING) {
                // Build neural_sparse query inline
                boolQuery.startObject("neural_sparse");
                boolQuery.startObject(MEMORY_EMBEDDING_FIELD);
                boolQuery.field("query_text", fact);
                boolQuery.field("model_id", storageConfig.getEmbeddingModelId());
                boolQuery.endObject();
                boolQuery.endObject();
            } else {
                throw new IllegalStateException("Unsupported embedding model type: " + storageConfig.getEmbeddingModelType());
            }
        } else {
            // Build match query inline
            boolQuery.startObject("match");
            boolQuery.field(MEMORY_FIELD, fact);
            boolQuery.endObject();
        }
        boolQuery.endObject();

        boolQuery.endArray(); // end must

        boolQuery.endObject(); // end bool
        boolQuery.endObject(); // end root

        return boolQuery;
    }
}
