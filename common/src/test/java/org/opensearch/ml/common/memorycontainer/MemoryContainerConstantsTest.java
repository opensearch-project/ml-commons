/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for MemoryContainerConstants to ensure constants are properly defined
 */
public class MemoryContainerConstantsTest {

    @Test
    public void testMemoryContainerFieldConstants() {
        assertEquals("memory_container_id", MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD);
        assertEquals("name", MemoryContainerConstants.NAME_FIELD);
        assertEquals("description", MemoryContainerConstants.DESCRIPTION_FIELD);
        assertEquals("owner", MemoryContainerConstants.OWNER_FIELD);
        assertEquals("created_time", MemoryContainerConstants.CREATED_TIME_FIELD);
        assertEquals("last_updated_time", MemoryContainerConstants.LAST_UPDATED_TIME_FIELD);
        assertEquals("memory_storage_config", MemoryContainerConstants.MEMORY_STORAGE_CONFIG_FIELD);
    }

    @Test
    public void testMemoryStorageConfigFieldConstants() {
        assertEquals("memory_index_name", MemoryContainerConstants.MEMORY_INDEX_NAME_FIELD);
        assertEquals("semantic_storage_enabled", MemoryContainerConstants.SEMANTIC_STORAGE_ENABLED_FIELD);
        assertEquals("embedding_model_type", MemoryContainerConstants.EMBEDDING_MODEL_TYPE_FIELD);
        assertEquals("embedding_model_id", MemoryContainerConstants.EMBEDDING_MODEL_ID_FIELD);
        assertEquals("llm_model_id", MemoryContainerConstants.LLM_MODEL_ID_FIELD);
        assertEquals("dimension", MemoryContainerConstants.DIMENSION_FIELD);
        assertEquals("max_infer_size", MemoryContainerConstants.MAX_INFER_SIZE_FIELD);
    }

    @Test
    public void testDefaultValues() {
        assertEquals(5, MemoryContainerConstants.MAX_INFER_SIZE_DEFAULT_VALUE);
    }

    @Test
    public void testIndexPrefixes() {
        assertEquals("ml-static-memory-", MemoryContainerConstants.STATIC_MEMORY_INDEX_PREFIX);
        assertEquals("ml-knn-memory-", MemoryContainerConstants.KNN_MEMORY_INDEX_PREFIX);
        assertEquals("ml-sparse-memory-", MemoryContainerConstants.SPARSE_MEMORY_INDEX_PREFIX);
    }

    @Test
    public void testMemoryDataFieldConstants() {
        assertEquals("user_id", MemoryContainerConstants.USER_ID_FIELD);
        assertEquals("agent_id", MemoryContainerConstants.AGENT_ID_FIELD);
        assertEquals("session_id", MemoryContainerConstants.SESSION_ID_FIELD);
        assertEquals("memory", MemoryContainerConstants.MEMORY_FIELD);
        assertEquals("memory_embedding", MemoryContainerConstants.MEMORY_EMBEDDING_FIELD);
        assertEquals("tags", MemoryContainerConstants.TAGS_FIELD);
        assertEquals("memory_id", MemoryContainerConstants.MEMORY_ID_FIELD);
        assertEquals("memory_type", MemoryContainerConstants.MEMORY_TYPE_FIELD);
        assertEquals("role", MemoryContainerConstants.ROLE_FIELD);
    }

    @Test
    public void testRequestFieldConstants() {
        assertEquals("message", MemoryContainerConstants.MESSAGE_FIELD);
        assertEquals("messages", MemoryContainerConstants.MESSAGES_FIELD);
        assertEquals("content", MemoryContainerConstants.CONTENT_FIELD);
        assertEquals("infer", MemoryContainerConstants.INFER_FIELD);
        assertEquals("query", MemoryContainerConstants.QUERY_FIELD);
        assertEquals("text", MemoryContainerConstants.TEXT_FIELD);
    }

    @Test
    public void testKnnIndexSettings() {
        assertEquals("lucene", MemoryContainerConstants.KNN_ENGINE);
        assertEquals("cosinesimil", MemoryContainerConstants.KNN_SPACE_TYPE);
        assertEquals("hnsw", MemoryContainerConstants.KNN_METHOD_NAME);
        assertEquals(100, MemoryContainerConstants.KNN_EF_SEARCH);
        assertEquals(100, MemoryContainerConstants.KNN_EF_CONSTRUCTION);
        assertEquals(16, MemoryContainerConstants.KNN_M);
    }

    @Test
    public void testRestApiPaths() {
        assertEquals("/_plugins/_ml/memory_containers", MemoryContainerConstants.BASE_MEMORY_CONTAINERS_PATH);
        assertEquals("/_plugins/_ml/memory_containers/_create", MemoryContainerConstants.CREATE_MEMORY_CONTAINER_PATH);
        assertEquals("memory_container_id", MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID);
        assertEquals("memory_id", MemoryContainerConstants.PARAMETER_MEMORY_ID);

        String expectedMemoriesPath = "/_plugins/_ml/memory_containers/{memory_container_id}/memories";
        assertEquals(expectedMemoriesPath, MemoryContainerConstants.MEMORIES_PATH);

        String expectedSearchPath = expectedMemoriesPath + "/_search";
        assertEquals(expectedSearchPath, MemoryContainerConstants.SEARCH_MEMORIES_PATH);

        String expectedDeletePath = expectedMemoriesPath + "/{memory_id}";
        assertEquals(expectedDeletePath, MemoryContainerConstants.DELETE_MEMORY_PATH);
        assertEquals(expectedDeletePath, MemoryContainerConstants.UPDATE_MEMORY_PATH);
    }

    @Test
    public void testResponseFields() {
        assertEquals("status", MemoryContainerConstants.STATUS_FIELD);
    }

    @Test
    public void testMemoryDecisionFields() {
        assertEquals("memory_decision", MemoryContainerConstants.MEMORY_DECISION_FIELD);
        assertEquals("old_memory", MemoryContainerConstants.OLD_MEMORY_FIELD);
        assertEquals("retrieved_facts", MemoryContainerConstants.RETRIEVED_FACTS_FIELD);
        assertEquals("event", MemoryContainerConstants.EVENT_FIELD);
        assertEquals("score", MemoryContainerConstants.SCORE_FIELD);
    }

    @Test
    public void testApiLimits() {
        assertEquals(10, MemoryContainerConstants.MAX_MESSAGES_PER_REQUEST);
        assertEquals("Cannot process more than 10 messages in a single request", MemoryContainerConstants.MAX_MESSAGES_EXCEEDED_ERROR);
    }

    @Test
    public void testErrorMessages() {
        // Test semantic storage error messages
        assertEquals(
            "Embedding model type is required when embedding model ID is provided",
            MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_TYPE_REQUIRED_ERROR
        );
        assertEquals(
            "Embedding model ID is required when embedding model type is provided",
            MemoryContainerConstants.SEMANTIC_STORAGE_EMBEDDING_MODEL_ID_REQUIRED_ERROR
        );
        assertEquals("Dimension is required for TEXT_EMBEDDING", MemoryContainerConstants.TEXT_EMBEDDING_DIMENSION_REQUIRED_ERROR);
        assertEquals("Dimension is not allowed for SPARSE_ENCODING", MemoryContainerConstants.SPARSE_ENCODING_DIMENSION_NOT_ALLOWED_ERROR);
        assertEquals(
            "Embedding model type must be either TEXT_EMBEDDING or SPARSE_ENCODING",
            MemoryContainerConstants.INVALID_EMBEDDING_MODEL_TYPE_ERROR
        );
        assertEquals("Maximum infer size cannot exceed 10", MemoryContainerConstants.MAX_INFER_SIZE_LIMIT_ERROR);
        assertTrue(MemoryContainerConstants.FIELD_NOT_ALLOWED_SEMANTIC_DISABLED_ERROR.contains("%s"));

        // Test model validation error messages
        assertTrue(MemoryContainerConstants.LLM_MODEL_NOT_FOUND_ERROR.contains("%s"));
        assertTrue(MemoryContainerConstants.LLM_MODEL_NOT_REMOTE_ERROR.contains("%s"));
        assertTrue(MemoryContainerConstants.EMBEDDING_MODEL_NOT_FOUND_ERROR.contains("%s"));
        assertTrue(MemoryContainerConstants.EMBEDDING_MODEL_TYPE_MISMATCH_ERROR.contains("%s"));
        assertEquals(
            "infer=true requires llm_model_id to be configured in memory storage",
            MemoryContainerConstants.INFER_REQUIRES_LLM_MODEL_ERROR
        );
    }

    @Test
    public void testLlmPrompts() {
        // Test Personal Information Organizer prompt
        assertNotNull(MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT);
        assertTrue(MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT.contains("Personal Information Organizer"));
        assertTrue(MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT.contains("Extract and organize personal information"));
        assertTrue(MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT.contains("\"facts\""));

        // Test Default Update Memory prompt
        assertNotNull(MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT);
        assertTrue(MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT.contains("smart memory manager"));
        assertTrue(MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT.contains("memory_decision"));
        assertTrue(MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT.contains("ADD|UPDATE|DELETE|NONE"));
    }

    @Test
    public void testPromptStructure() {
        // Verify Personal Information Organizer prompt has proper XML structure
        String organizerPrompt = MemoryContainerConstants.PERSONAL_INFORMATION_ORGANIZER_PROMPT;
        assertTrue(organizerPrompt.startsWith("<system_prompt>"));
        assertTrue(organizerPrompt.contains("<role>"));
        assertTrue(organizerPrompt.contains("<objective>"));
        assertTrue(organizerPrompt.contains("<instructions>"));
        assertTrue(organizerPrompt.contains("<response_format>"));
        assertTrue(organizerPrompt.contains("</system_prompt>"));

        // Verify Update Memory prompt has proper XML structure
        String updatePrompt = MemoryContainerConstants.DEFAULT_UPDATE_MEMORY_PROMPT;
        assertTrue(updatePrompt.startsWith("<system_prompt>"));
        assertTrue(updatePrompt.contains("<role>"));
        assertTrue(updatePrompt.contains("<task>"));
        assertTrue(updatePrompt.contains("<response_format>"));
        assertTrue(updatePrompt.contains("<operations>"));
        assertTrue(updatePrompt.contains("<guidelines>"));
        assertTrue(updatePrompt.contains("<example>"));
        assertTrue(updatePrompt.contains("</system_prompt>"));
    }

    @Test
    public void testConstantsConsistency() {
        // Verify that DELETE and UPDATE paths use the same pattern
        assertEquals(MemoryContainerConstants.DELETE_MEMORY_PATH, MemoryContainerConstants.UPDATE_MEMORY_PATH);

        // Verify that parameter names are used in path construction
        assertTrue(MemoryContainerConstants.MEMORIES_PATH.contains("{" + MemoryContainerConstants.PARAMETER_MEMORY_CONTAINER_ID + "}"));
        assertTrue(MemoryContainerConstants.DELETE_MEMORY_PATH.contains("{" + MemoryContainerConstants.PARAMETER_MEMORY_ID + "}"));
    }
}
