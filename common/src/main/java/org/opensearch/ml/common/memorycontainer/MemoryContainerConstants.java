/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Constants for Memory Container feature
 */
public class MemoryContainerConstants {

    // Field names for MemoryContainer
    public static final String CONTAINER_ID_FIELD = "container_id";
    public static final String CONTAINER_NAME_FIELD = "container_name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String OWNER_FIELD = "owner";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String INDEX_NAME_FIELD = "index_name";
    public static final String SEMANTIC_STORAGE_FIELD = "semantic_storage";
    public static final String TENANT_ID_FIELD = "tenant_id";

    // Field names for SemanticStorageConfig
    public static final String SEMANTIC_STORAGE_ENABLED_FIELD = "semantic_storage_enabled";
    public static final String MODEL_TYPE_FIELD = "model_type";
    public static final String MODEL_ID_FIELD = "model_id";
    public static final String DIMENSION_FIELD = "dimension";

    // Memory index type prefixes
    public static final String STATIC_MEMORY_INDEX_PREFIX = "ml-static-memory-";
    public static final String KNN_MEMORY_INDEX_PREFIX = "ml-knn-memory-";
    public static final String SPARSE_MEMORY_INDEX_PREFIX = "ml-sparse-memory-";

    // Memory data index field names
    public static final String USER_ID_FIELD = "user_id";
    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String SESSION_ID_FIELD = "session_id";
    public static final String RAW_MESSAGES_FIELD = "raw_messages";
    public static final String FACT_FIELD = "fact";
    public static final String FACT_ENCODING_FIELD = "fact_encoding";
    public static final String TAGS_FIELD = "tags";

    // KNN index settings
    public static final String KNN_ENGINE = "faiss";
    public static final String KNN_SPACE_TYPE = "cosinesimil";
    public static final String KNN_METHOD_NAME = "hnsw";
    public static final int KNN_EF_SEARCH = 100;
    public static final int KNN_EF_CONSTRUCTION = 128;
    public static final int KNN_M = 24;

    // Response fields
    public static final String STATUS_FIELD = "status";

    // Error messages
    public static final String SEMANTIC_STORAGE_MODEL_TYPE_REQUIRED_ERROR = "Model type is required when semantic storage is enabled";
    public static final String SEMANTIC_STORAGE_MODEL_ID_REQUIRED_ERROR = "Model ID is required when semantic storage is enabled";
    public static final String TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR = "Dimension is required for TEXT_EMBEDDING";
    public static final String SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR = "Dimension is not allowed for SPARSE_ENCODING";
    public static final String INVALID_MODEL_TYPE_ERROR = "Model type must be either TEXT_EMBEDDING or SPARSE_ENCODING";
}
