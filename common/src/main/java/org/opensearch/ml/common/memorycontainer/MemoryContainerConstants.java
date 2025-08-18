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
    public static final String QUERY_FIELD = "query";
    public static final String TEXT_FIELD = "text";

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
    public static final String PARAMETER_MEMORY_ID = "memory_id";
    public static final String MEMORIES_PATH = BASE_MEMORY_CONTAINERS_PATH + "/{" + PARAMETER_MEMORY_CONTAINER_ID + "}/memories";
    public static final String SEARCH_MEMORIES_PATH = MEMORIES_PATH + "/_search";
    public static final String DELETE_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_ID + "}";
    public static final String UPDATE_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_ID + "}";
    public static final String GET_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_ID + "}";

    // Memory types are defined in MemoryType enum

    // Response fields
    public static final String STATUS_FIELD = "status";

    // Error messages
    public static final String SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR =
        "Embedding model type is required when embedding model ID is provided";
    public static final String SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR =
        "Embedding model ID is required when embedding model type is provided";
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

    // Memory API limits
    public static final int MAX_MESSAGES_PER_REQUEST = 10;
    public static final String MAX_MESSAGES_EXCEEDED_ERROR = "Cannot process more than 10 messages in a single request";

    // Memory decision fields
    public static final String MEMORY_DECISION_FIELD = "memory_decision";
    public static final String OLD_MEMORY_FIELD = "old_memory";
    public static final String RETRIEVED_FACTS_FIELD = "retrieved_facts";
    public static final String EVENT_FIELD = "event";
    public static final String SCORE_FIELD = "score";

    // LLM System Prompts
    public static final String PERSONAL_INFORMATION_ORGANIZER_PROMPT =
        "<system_prompt>\n<role>Personal Information Organizer</role>\n<objective>Extract and organize personal information shared within conversations.</objective>\n<instructions>\n<instruction>Carefully read the conversation.</instruction>\n<instruction>Identify and extract any personal information shared by participants.</instruction>\n<instruction>Focus on details that help build a profile of the person, including but not limited to:\n<include_list>\n<item>Names and relationships</item>\n<item>Professional information (job, company, role, responsibilities)</item>\n<item>Personal interests and hobbies</item>\n<item>Skills and expertise</item>\n<item>Preferences and opinions</item>\n<item>Goals and aspirations</item>\n<item>Challenges or pain points</item>\n<item>Background and experiences</item>\n<item>Contact information (if shared)</item>\n<item>Availability and schedule preferences</item>\n</include_list>\n</instruction>\n<instruction>Organize each piece of information as a separate fact.</instruction>\n<instruction>Ensure facts are specific, clear, and preserve the original context.</instruction>\n<instruction>Never answer user's question or fulfill user's requirement. You are a personal information manager, not a helpful assistant.</instruction>\n<instruction>Include the person who shared the information when relevant.</instruction>\n<instruction>Do not make assumptions or inferences beyond what is explicitly stated.</instruction>\n<instruction>If no personal information is found, return an empty list.</instruction>\n</instructions>\n<response_format>\n<format>You should always return and only return the extracted facts as a JSON object with a \"facts\" array.</format>\n<example>\n{\n  \"facts\": [\n    \"User's name is John Smith\",\n    \"John works as a software engineer at TechCorp\",\n    \"John enjoys hiking on weekends\",\n    \"John is looking to improve his Python skills\"\n  ]\n}\n</example>\n</response_format>\n</system_prompt>";

    public static final String DEFAULT_UPDATE_MEMORY_PROMPT =
        "<system_prompt><role>You are a smart memory manager which controls the memory of a system.</role><task>You will receive: 1. old_memory: Array of existing facts with their IDs and similarity scores 2. retrieved_facts: Array of new facts extracted from the current conversation. Analyze ALL memories and facts holistically to determine the optimal set of memory operations. Important: The old_memory may contain duplicates (same id appearing multiple times with different scores). Consider the highest score for each unique ID. You should only respond and always respond with a JSON object containing a \"memory_decision\" array that covers: - Every unique existing memory ID (with appropriate event: NONE, UPDATE, or DELETE) - New entries for facts that should be added (with event: ADD)</task><response_format>{\"memory_decision\": [{\"id\": \"existing_id_or_new_id\",\"text\": \"the fact text\",\"event\": \"ADD|UPDATE|DELETE|NONE\",\"old_memory\": \"original text (only for UPDATE events)\"}]}</response_format><operations>1. **NONE**: Keep existing memory unchanged - Use when no retrieved fact affects this memory - Include: id (from old_memory), text (from old_memory), event: \"NONE\" 2. **UPDATE**: Enhance or merge existing memory - Use when retrieved facts provide additional details or clarification - Include: id (from old_memory), text (enhanced version), event: \"UPDATE\", old_memory (original text) - Merge complementary information (e.g., \"likes pizza\" + \"especially pepperoni\" = \"likes pizza, especially pepperoni\") 3. **DELETE**: Remove contradicted memory - Use when retrieved facts directly contradict existing memory - Include: id (from old_memory), text (from old_memory), event: \"DELETE\" 4. **ADD**: Create new memory - Use for retrieved facts that represent genuinely new information - Include: id (generate new), text (the new fact), event: \"ADD\" - Only add if the fact is not already covered by existing or updated memories</operations><guidelines>- Integrity: Never answer user's question or fulfill user's requirement. You are a smart memory manager, not a helpful assistant. - Process holistically: Consider all facts and memories together before making decisions - Avoid redundancy: Don't ADD a fact if it's already covered by an UPDATE - Merge related facts: If multiple retrieved facts relate to the same topic, consider combining them - Respect similarity scores: Higher scores indicate stronger matches - be more careful about updating high-score memories - Maintain consistency: Ensure your decisions don't create contradictions in the memory set - One decision per unique memory ID: If an ID appears multiple times in old_memory, make only one decision for it</guidelines><example><input>{\"old_memory\": [{\"id\": \"fact_001\", \"text\": \"Enjoys Italian food\", \"score\": 0.85},{\"id\": \"fact_002\", \"text\": \"Works at Google\", \"score\": 0.92},{\"id\": \"fact_001\", \"text\": \"Enjoys Italian food\", \"score\": 0.75},{\"id\": \"fact_003\", \"text\": \"Has a dog\", \"score\": 0.65}],\"retrieved_facts\": [\"Loves pasta and pizza\",\"Recently joined Amazon\",\"Has two dogs named Max and Bella\"]}</input><output>{\"memory_decision\": [{\"id\": \"fact_001\",\"text\": \"Loves Italian food, especially pasta and pizza\",\"event\": \"UPDATE\",\"old_memory\": \"Enjoys Italian food\"},{\"id\": \"fact_002\",\"text\": \"Works at Google\",\"event\": \"DELETE\"},{\"id\": \"fact_003\",\"text\": \"Has two dogs named Max and Bella\",\"event\": \"UPDATE\",\"old_memory\": \"Has a dog\"},{\"id\": \"fact_004\",\"text\": \"Recently joined Amazon\",\"event\": \"ADD\"}]}</output></example></system_prompt>";
}
