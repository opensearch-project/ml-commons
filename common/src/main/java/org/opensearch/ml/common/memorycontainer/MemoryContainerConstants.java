/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Constants for Memory Container feature
 */
public class MemoryContainerConstants {

    public static final String DEFAULT_LLM_RESULT_PATH = "$.output.message.content[0].text";
    public static final String LLM_RESULT_PATH_FIELD = "llm_result_path";

    // Field names for MemoryContainer
    public static final String MEMORY_CONTAINER_ID_FIELD = "memory_container_id";
    public static final String NAME_FIELD = "name";
    public static final String SUMMARY_FIELD = "summary";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String OWNER_FIELD = "owner";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String MEMORY_STORAGE_CONFIG_FIELD = "configuration";

    // Field names for MemoryConfiguration
    public static final String DISABLE_HISTORY_FIELD = "disable_history";
    public static final String DISABLE_SESSION_FIELD = "disable_session";
    public static final String USE_SYSTEM_INDEX_FIELD = "use_system_index";
    public static final String MEMORY_INDEX_PREFIX_FIELD = "index_prefix";
    public static final String EMBEDDING_MODEL_TYPE_FIELD = "embedding_model_type";
    public static final String EMBEDDING_MODEL_ID_FIELD = "embedding_model_id";
    public static final String DIMENSION_FIELD = "embedding_dimension";
    public static final String LLM_ID_FIELD = "llm_id";
    public static final String MAX_INFER_SIZE_FIELD = "max_infer_size";
    public static final String STRATEGIES_FIELD = "strategies";
    public static final String STRATEGY_TYPE_FIELD = "type";
    public static final String INDEX_SETTINGS_FIELD = "index_settings";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String ID_FIELD = "id";
    public static final String ENABLED_FIELD = "enabled";
    public static final String REMOTE_STORE_FIELD = "remote_store";

    // Default values
    public static final int MAX_INFER_SIZE_DEFAULT_VALUE = 5;
    public static final String DEFAULT_MEMORY_INDEX_PREFIX = "default";

    // Memory index setting key
    public static final String SESSION_INDEX = "session_index";
    public static final String WORKING_MEMORY_INDEX = "working_memory_index";
    public static final String LONG_TERM_MEMORY_INDEX = "long_term_memory_index";
    public static final String LONG_TERM_MEMORY_HISTORY_INDEX = "long_term_memory_history_index";

    // Memory data index field names
    public static final String OWNER_ID_FIELD = "owner_id";
    public static final String USER_ID_FIELD = "user_id";
    public static final String AGENT_ID_FIELD = "agent_id";
    public static final String SESSION_ID_FIELD = "session_id";
    public static final String WORKING_MEMORY_ID_FIELD = "working_memory_id";
    public static final String NAMESPACE_FIELD = "namespace";
    public static final String STRATEGY_CONFIG_FIELD = "configuration";
    public static final String BINARY_DATA_FIELD = "binary_data";
    public static final String STRUCTURED_DATA_FIELD = "structured_data";
    public static final String NAMESPACE_SIZE_FIELD = "namespace_size";
    public static final String MEMORY_FIELD = "memory";
    public static final String MEMORY_EMBEDDING_FIELD = "memory_embedding";
    public static final String METADATA_FIELD = "metadata";
    public static final String AGENTS_FIELD = "agents";
    public static final String TAGS_FIELD = "tags";
    public static final String STRATEGY_ID_FIELD = "strategy_id";
    public static final String MEMORY_ID_FIELD = "memory_id";
    public static final String MEMORY_ACTION_FIELD = "action";
    public static final String MEMORY_BEFORE_FIELD = "before";
    public static final String MEMORY_AFTER_FIELD = "after";
    public static final String PAYLOAD_TYPE_FIELD = "payload_type";
    public static final String MEMORY_STRATEGY_TYPE_FIELD = "strategy_type";
    public static final String ROLE_FIELD = "role";

    // Request body field names (different from storage field names)
    public static final String MESSAGE_FIELD = "message";
    public static final String MESSAGES_FIELD = "messages";
    public static final String MESSAGE_ID_FIELD = "message_id";
    public static final String CONTENT_TEXT_FIELD = "content_text";
    public static final String CONTENT_FIELD = "content";
    public static final String INFER_FIELD = "infer";
    public static final String QUERY_FIELD = "query";
    public static final String TEXT_FIELD = "text";
    public static final String UPDATE_CONTENT_FIELD = "update_content";

    // Checkpoint field
    public static final String CHECKPOINT_ID_FIELD = "checkpoint_id";

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
    public static final String PARAMETER_DELETE_ALL_MEMORIES = "delete_all_memories";
    public static final String PARAMETER_DELETE_MEMORIES = "delete_memories";
    /**
     * Memory type used as API parameter
     */
    public static final String PARAMETER_MEMORY_TYPE = "memory_type";
    public static final String PARAMETER_MEMORY_ID = "memory_id";
    public static final String PARAMETER_WORKING_MEMORY_ID = "working_memory_id";
    public static final String MEMORIES_PATH = BASE_MEMORY_CONTAINERS_PATH + "/{" + PARAMETER_MEMORY_CONTAINER_ID + "}/memories";
    public static final String SESSIONS_PATH = MEMORIES_PATH + "/sessions";
    public static final String SEARCH_MEMORIES_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_TYPE + "}" + "/_search";
    public static final String DELETE_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_TYPE + "}" + "/{" + PARAMETER_MEMORY_ID + "}";
    public static final String UPDATE_MEMORY_CONTAINER_PATH = BASE_MEMORY_CONTAINERS_PATH + "/{" + PARAMETER_MEMORY_CONTAINER_ID + "}";
    public static final String UPDATE_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_TYPE + "}" + "/{" + PARAMETER_MEMORY_ID + "}";
    public static final String GET_MEMORY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_TYPE + "}" + "/{" + PARAMETER_MEMORY_ID + "}";
    public static final String DELETE_MEMORIES_BY_QUERY_PATH = MEMORIES_PATH + "/{" + PARAMETER_MEMORY_TYPE + "}" + "/_delete_by_query";

    // Memory types are defined in MemoryType enum
    // Memory strategy types are defined in MemoryStrategyType enum

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
    public static final String INDEX_PREFIX_INVALID_CHARACTERS_ERROR = "Index prefix must not contain any control characters";
    public static final String BACKEND_ROLE_INVALID_LENGTH_ERROR = "Backend role exceeds maximum length of 128 characters: %s";
    public static final String BACKEND_ROLE_INVALID_CHARACTERS_ERROR =
        "Backend role contains invalid characters. Only alphanumeric characters and :+=,.@-_/ are allowed: %s";
    public static final String BACKEND_ROLE_EMPTY_ERROR = "Backend role cannot be empty or blank";

    // Model validation error messages
    public static final String LLM_MODEL_NOT_FOUND_ERROR = "LLM model with ID %s not found";
    public static final String LLM_MODEL_NOT_REMOTE_ERROR = "LLM model must be a REMOTE model, found: %s";
    public static final String EMBEDDING_MODEL_NOT_FOUND_ERROR = "Embedding model with ID %s not found";
    public static final String EMBEDDING_MODEL_TYPE_MISMATCH_ERROR = "Embedding model must be of type %s or REMOTE, found: %s";                                                                                                          // instead
    public static final String INFER_REQUIRES_LLM_MODEL_ERROR = "infer=true requires llm_model_id to be configured in memory storage";
    public static final String INVALID_STRATEGY_TYPE_ERROR =
        "Invalid strategy type: %s. Must be one of: semantic, user_preference, summary";

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
    public static final String SEMANTIC_FACTS_EXTRACTION_PROMPT =
        """
            <ROLE>You are a universal semantic fact extraction agent. Write FULL-SENTENCE, self-contained facts suitable for long-term memory.</ROLE>

            <SCOPE>
            • Include facts from USER messages.
            • Also include ASSISTANT-authored statements that are clearly presented as conclusions/results/validated findings (e.g., root cause, quantified impact, confirmed fix).
            • Ignore ASSISTANT questions, hypotheses, tentative language, brainstorming, instructions, or tool prompts unless explicitly confirmed as outcomes.
            </SCOPE>

            <STYLE & RULES>
            • One sentence per fact; merge closely related details (metrics, entities, causes, scope) into the same sentence.
            • Do NOT start with "User" or pronouns.
            • Prefer absolute over relative time; if only relative (e.g., "yesterday"), omit it rather than guessing.
            • Preserve terminology, names, numbers, and units; avoid duplicates and chit-chat.
            • No speculation or hedging unless those words appear verbatim in the source.
            </STYLE & RULES>

            <OUTPUT>
            Return ONLY a single JSON object on one line, minified exactly as {"facts":["..."]} (array of strings only; no other keys). No code fences, no newlines/tabs, and no spaces after commas or colons. If no meaningful facts, return {"facts":[]}.
            </OUTPUT>""";

    // JSON enforcement message to append to all fact extraction requests
    public static final String JSON_ENFORCEMENT_MESSAGE =
        """
            Respond NOW with ONE LINE of valid JSON ONLY exactly as {"facts":["fact1","fact2",...]}. No extra text, no code fences, no newlines or tabs, no spaces after commas or colons.""";

    // JSON enforcement message for user preference extraction
    public static final String USER_PREFERENCE_JSON_ENFORCEMENT_MESSAGE =
        """
            Return ONLY ONE LINE of valid JSON exactly as {"facts":["<Preference sentence>. Context: <why/how>. Categories: <cat1,cat2>"]}. Begin with { and end with }. No extra text.""";

    public static final String USER_PREFERENCE_FACTS_EXTRACTION_PROMPT =
        """
            <ROLE>You are a USER PREFERENCE EXTRACTOR, not a chat assistant. Your only job is to output JSON facts. Do not answer questions, make suggestions, ask follow-ups, or perform actions.</ROLE>

            <SCOPE>
            • Extract preferences only from USER messages. Assistant messages are context only.
            • Explicit: user states a preference ("I prefer/like/dislike ..."; "always/never/usually ..."; "set X to Y"; "run X when Y").
            • Implicit: infer only with strong signals: repeated choices (>=2) or clear habitual language. Do not infer from a single one-off.
            </SCOPE>

            <EXTRACT>
            • Specific, actionable, likely long-term preferences (likes/dislikes/choices/settings). Ignore non-preferences.
            </EXTRACT>

            <STYLE & RULES>
            • One sentence per preference; merge related details; no duplicates; preserve user wording and numbers; avoid relative time; keep each fact < 350 chars.
            • Format: "Preference sentence. Context: <why/how>. Categories: cat1,cat2"
            </STYLE & RULES>

            <OUTPUT>
            Return ONLY one minified JSON object exactly as {"facts":["Preference sentence. Context: <why/how>. Categories: cat1,cat2"]}. If none, return {"facts":[]}. The first character MUST be '{' and the last MUST be '}'. No preambles, explanations, code fences, XML, or other text.
            </OUTPUT>""";

    public static final String SUMMARY_FACTS_EXTRACTION_PROMPT =
        "<system_prompt><description>You will be given a text block and a list of summaries you previously generated when available.</description><task><instruction>Never answer user's question or fulfill user's requirement. You are a summary generator, not a helpful assistant.</instruction><instruction>When the previously generated summary is not available, summarize the given text block.</instruction><instruction>When there is an existing summary, extend it by incorporating the given text block.</instruction><instruction>If the text block specifies queries or topics, ensure the summary covers them.</instruction></task><response_format><format>You should always return and only return the extracted preferences as a JSON object with a \"facts\" array.</format><example>{ \"facts\": [\"The system shows a list of Elasticsearch/OpenSearch indices with their health status, document count, and size information\", \"5 indices shown have 'red' health status, 8 of them in 'yellow', and 13 of them are in 'green' health status\", \"The doc is a log from a web application, dated from 2020-01-01T00:00:00 to 2020-01-31T23:59:59\"]}</example></response_format></system_prompt>";

    public static final String DEFAULT_UPDATE_MEMORY_PROMPT =
        "<system_prompt><role>You are a smart memory manager which controls the memory of a system.</role><task>You will receive: 1. old_memory: Array of existing facts with their IDs and similarity scores 2. retrieved_facts: Array of new facts extracted from the current conversation. Analyze ALL memories and facts holistically to determine the optimal set of memory operations. Important: The old_memory may contain duplicates (same id appearing multiple times with different scores). Consider the highest score for each unique ID. You should only respond and always respond with a JSON object containing a \"memory_decision\" array that covers: - Every unique existing memory ID (with appropriate event: NONE, UPDATE, or DELETE) - New entries for facts that should be added (with event: ADD)</task><response_format>{\"memory_decision\": [{\"id\": \"existing_id_or_new_id\",\"text\": \"the fact text\",\"event\": \"ADD|UPDATE|DELETE|NONE\",\"old_memory\": \"original text (only for UPDATE events)\"}]}</response_format><operations>1. **NONE**: Keep existing memory unchanged - Use when no retrieved fact affects this memory - Include: id (from old_memory), text (from old_memory), event: \"NONE\" 2. **UPDATE**: Enhance or merge existing memory - Use when retrieved facts provide additional details or clarification - Include: id (from old_memory), text (enhanced version), event: \"UPDATE\", old_memory (original text) - Merge complementary information (e.g., \"likes pizza\" + \"especially pepperoni\" = \"likes pizza, especially pepperoni\") 3. **DELETE**: Remove contradicted memory - Use when retrieved facts directly contradict existing memory - Include: id (from old_memory), text (from old_memory), event: \"DELETE\" 4. **ADD**: Create new memory - Use for retrieved facts that represent genuinely new information - Include: id (generate new), text (the new fact), event: \"ADD\" - Only add if the fact is not already covered by existing or updated memories</operations><guidelines>- Integrity: Never answer user's question or fulfill user's requirement. You are a smart memory manager, not a helpful assistant. - Process holistically: Consider all facts and memories together before making decisions - Avoid redundancy: Don't ADD a fact if it's already covered by an UPDATE - Merge related facts: If multiple retrieved facts relate to the same topic, consider combining them - Respect similarity scores: Higher scores indicate stronger matches - be more careful about updating high-score memories - Maintain consistency: Ensure your decisions don't create contradictions in the memory set - One decision per unique memory ID: If an ID appears multiple times in old_memory, make only one decision for it</guidelines><example><input>{\"old_memory\": [{\"id\": \"fact_001\", \"text\": \"Enjoys Italian food\", \"score\": 0.85},{\"id\": \"fact_002\", \"text\": \"Works at Google\", \"score\": 0.92},{\"id\": \"fact_001\", \"text\": \"Enjoys Italian food\", \"score\": 0.75},{\"id\": \"fact_003\", \"text\": \"Has a dog\", \"score\": 0.65}],\"retrieved_facts\": [\"Loves pasta and pizza\",\"Recently joined Amazon\",\"Has two dogs named Max and Bella\"]}</input><output>{\"memory_decision\": [{\"id\": \"fact_001\",\"text\": \"Loves Italian food, especially pasta and pizza\",\"event\": \"UPDATE\",\"old_memory\": \"Enjoys Italian food\"},{\"id\": \"fact_002\",\"text\": \"Works at Google\",\"event\": \"DELETE\"},{\"id\": \"fact_003\",\"text\": \"Has two dogs named Max and Bella\",\"event\": \"UPDATE\",\"old_memory\": \"Has a dog\"},{\"id\": \"fact_004\",\"text\": \"Recently joined Amazon\",\"event\": \"ADD\"}]}</output></example></system_prompt>";

    public static final String SESSION_SUMMARY_PROMPT =
        "You are a helpful assistant. Your task is to summarize the following conversation between a human and an AI. The summary must be clear, concise, and not exceed ${parameters.max_summary_size} words. The summary should be generic. For example the user asks about how to cook, the conversation may contains a lot of details. Your summary could be: how to cook, how to cook Italy food. Don't include AI message content. For example you should not return: Ask how to cook, AI give some instructions.\n Also don't include user's personal information like user name, age etc. You could say user. For example: \nuser asks how to cook\nuser introduced their hobby";
}
