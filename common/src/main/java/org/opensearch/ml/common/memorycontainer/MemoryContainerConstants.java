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
    public static final String MEMORY_CONTAINER_ID_FIELD = "memory_container_id";
    public static final String NAME_FIELD = "name";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String OWNER_FIELD = "owner";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String MEMORY_STORAGE_CONFIG_FIELD = "memory_storage_config";

    // Field names for MemoryStorageConfig
    public static final String MEMORY_INDEX_NAME_FIELD = "memory_index_name";
    public static final String SEMANTIC_STORAGE_ENABLED_FIELD = "semantic_storage_enabled";
    public static final String EMBEDDING_MODEL_TYPE_FIELD = "embedding_model_type";
    public static final String EMBEDDING_MODEL_ID_FIELD = "embedding_model_id";
    public static final String LLM_MODEL_ID_FIELD = "llm_model_id";
    public static final String DIMENSION_FIELD = "dimension";
    public static final String MAX_INFER_SIZE_FIELD = "max_infer_size";

    // Default values
    public static final int MAX_INFER_SIZE_DEFAULT_VALUE = 5;

    // Memory index type prefixes
    public static final String STATIC_MEMORY_INDEX_PREFIX = "ml-static-memory-";
    public static final String KNN_MEMORY_INDEX_PREFIX = "ml-knn-memory-";
    public static final String SPARSE_MEMORY_INDEX_PREFIX = "ml-sparse-memory-";

    // Memory data index field names
    public static final String USER_ID_FIELD = "user_id";
    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String SESSION_ID_FIELD = "session_id";
    public static final String MEMORY_FIELD = "memory";
    public static final String MEMORY_EMBEDDING_FIELD = "memory_embedding";
    public static final String TAGS_FIELD = "tags";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String MEMORY_TYPE_FIELD = "memory_type";
    public static final String ROLE_FIELD = "role";

    // Request body field names (different from storage field names)
    public static final String MESSAGE_FIELD = "message";
    public static final String MESSAGES_FIELD = "messages";
    public static final String CONTENT_FIELD = "content";
    public static final String INFER_FIELD = "infer";

    // KNN index settings
    public static final String KNN_ENGINE = "lucene";
    public static final String KNN_SPACE_TYPE = "cosinesimil";
    public static final String KNN_METHOD_NAME = "hnsw";
    public static final int KNN_EF_SEARCH = 100;
    public static final int KNN_EF_CONSTRUCTION = 100;
    public static final int KNN_M = 16;

    // REST API paths
    public static final String BASE_MEMORY_CONTAINERS_PATH = "/_plugins/_ml/memory_containers";
    public static final String CREATE_MEMORY_CONTAINER_PATH = BASE_MEMORY_CONTAINERS_PATH + "/_create";
    public static final String PARAMETER_MEMORY_CONTAINER_ID = "memory_container_id";
    public static final String MEMORIES_PATH = BASE_MEMORY_CONTAINERS_PATH + "/{" + PARAMETER_MEMORY_CONTAINER_ID + "}/memories";

    // Memory types are defined in MemoryType enum

    // Response fields
    public static final String STATUS_FIELD = "status";

    // Error messages
    public static final String SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR =
        "Embedding model type is required when embedding model ID is provided";
    public static final String SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR =
        "Embedding model ID is required when embedding model type is provided";
    public static final String SEMANTIC_STORAGE_LLM_MODEL_ID_REQUIRED_ERROR = "LLM model ID is required when semantic storage is enabled";
    public static final String TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR = "Dimension is required for TEXT_EMBEDDING";
    public static final String SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR = "Dimension is not allowed for SPARSE_ENCODING";
    public static final String INVALID_EMBEDDING_MODEL_TYPE_ERROR = "Embedding model type must be either TEXT_EMBEDDING or SPARSE_ENCODING";
    public static final String MAX_INFER_SIZE_LIMIT_ERROR = "Maximum infer size cannot exceed 10";
    public static final String FIELD_NOT_ALLOWED_SEMANTIC_DISABLED_ERROR = "Field %s is not allowed when semantic storage is disabled";

    // Model validation error messages
    public static final String LLM_MODEL_NOT_FOUND_ERROR = "LLM model with ID %s not found";
    public static final String LLM_MODEL_NOT_REMOTE_ERROR = "LLM model must be a REMOTE model, found: %s";
    public static final String EMBEDDING_MODEL_NOT_FOUND_ERROR = "Embedding model with ID %s not found";
    public static final String EMBEDDING_MODEL_TYPE_MISMATCH_ERROR = "Embedding model must be of type %s or REMOTE, found: %s";                                                                                                          // instead
    public static final String INFER_REQUIRES_LLM_MODEL_ERROR = "infer=true requires llm_model_id to be configured in memory storage";
}
