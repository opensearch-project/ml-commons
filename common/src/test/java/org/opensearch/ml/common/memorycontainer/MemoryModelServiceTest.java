/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.agent.MLAgentModelSpec;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

public class MemoryModelServiceTest {

    @Test
    public void testCreateModelFromSpec_bedrockEmbedding() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("amazon.titan-embed-text-v2:0")
            .modelProvider("bedrock/embedding")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testCreateModelFromSpec_bedrockConverseLLM() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
            .modelProvider("bedrock/converse")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
    }

    @Test
    public void testCreateModelFromSpec_nullSpec() {
        assertThrows(IllegalArgumentException.class, () -> MemoryModelService.createModelFromSpec(null));
    }

    @Test
    public void testCreateModelFromSpec_nullModelId() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MemoryModelService
                .createModelFromSpec(MLAgentModelSpec.builder().modelId(null).modelProvider("bedrock/embedding").build())
        );
    }

    @Test
    public void testCreateModelFromSpec_nullProvider() {
        assertThrows(
            IllegalArgumentException.class,
            () -> MemoryModelService.createModelFromSpec(MLAgentModelSpec.builder().modelId("test").modelProvider(null).build())
        );
    }

    @Test
    public void testDetectEmbeddingType_knownModel() {
        assertEquals(FunctionName.TEXT_EMBEDDING, MemoryModelService.detectEmbeddingType("amazon.titan-embed-text-v2:0"));
    }

    @Test
    public void testDetectEmbeddingType_unknownModel() {
        assertNull(MemoryModelService.detectEmbeddingType("unknown-model"));
    }

    @Test
    public void testDetectEmbeddingDimension_knownModel() {
        assertEquals(Integer.valueOf(1024), MemoryModelService.detectEmbeddingDimension("amazon.titan-embed-text-v2:0"));
        assertEquals(Integer.valueOf(1536), MemoryModelService.detectEmbeddingDimension("amazon.titan-embed-text-v1"));
    }

    @Test
    public void testDetectEmbeddingDimension_unknownModel() {
        assertNull(MemoryModelService.detectEmbeddingDimension("unknown-model"));
    }

    @Test
    public void testCreateModelFromSpec_memoryLlm_bedrock() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-sonnet-4-6")
            .modelProvider("bedrock/converse")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec, true);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
        ConnectorAction action = input.getConnector().getActions().get(0);
        assertTrue(action.getUrl().contains("/converse"));
        assertTrue(action.getRequestBody().contains("${parameters.system_prompt}"));
        assertTrue(action.getRequestBody().contains("${parameters.user_prompt}"));
    }

    @Test
    public void testCreateModelFromSpec_memoryLlm_openai() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("gpt-4o-mini")
            .modelProvider("openai/v1/chat/completions")
            .credential(Map.of("openAI_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec, true);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
        ConnectorAction action = input.getConnector().getActions().get(0);
        assertTrue(action.getUrl().contains("api.openai.com"));
        assertTrue(action.getHeaders().containsKey("Authorization"));
        assertTrue(action.getRequestBody().contains("${parameters.system_prompt}"));
    }

    @Test
    public void testCreateModelFromSpec_memoryLlm_gemini() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("gemini-2.0-flash")
            .modelProvider("gemini/v1beta/generatecontent")
            .credential(Map.of("gemini_api_key", "test"))
            .build();

        MLRegisterModelInput input = MemoryModelService.createModelFromSpec(spec, true);
        assertNotNull(input);
        assertEquals(FunctionName.REMOTE, input.getFunctionName());
        ConnectorAction action = input.getConnector().getActions().get(0);
        assertTrue(action.getUrl().contains("generativelanguage.googleapis.com"));
        assertTrue(action.getHeaders().containsKey("x-goog-api-key"));
        assertFalse(action.getUrl().contains("key="));
        assertTrue(action.getRequestBody().contains("${parameters.system_prompt}"));
    }

    @Test
    public void testCreateModelFromSpec_memoryLlm_unsupportedProvider() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("test")
            .modelProvider("unknown/provider")
            .credential(Map.of("key", "test"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> MemoryModelService.createModelFromSpec(spec, true));
    }

    @Test
    public void testCreateModelFromSpec_memoryLlm_bedrockRegionValidation() {
        MLAgentModelSpec spec = MLAgentModelSpec
            .builder()
            .modelId("us.anthropic.claude-sonnet-4-6")
            .modelProvider("bedrock/converse")
            .credential(Map.of("access_key", "test", "secret_key", "test"))
            .modelParameters(Map.of("region", "attacker.com"))
            .build();

        assertThrows(IllegalArgumentException.class, () -> MemoryModelService.createModelFromSpec(spec, true));
    }

    @Test
    public void testGetLlmResultPath_bedrock() {
        assertEquals("$.output.message.content[0].text", MemoryModelService.getLlmResultPath("bedrock/converse"));
    }

    @Test
    public void testGetLlmResultPath_openai() {
        assertEquals("$.choices[0].message.content", MemoryModelService.getLlmResultPath("openai/v1/chat/completions"));
    }

    @Test
    public void testGetLlmResultPath_gemini() {
        assertEquals("$.candidates[0].content.parts[0].text", MemoryModelService.getLlmResultPath("gemini/v1beta/generatecontent"));
    }

    @Test
    public void testGetLlmResultPath_unknown() {
        assertNull(MemoryModelService.getLlmResultPath("unknown"));
    }

    @Test
    public void testGetLlmResultPath_null() {
        assertNull(MemoryModelService.getLlmResultPath(null));
    }

    @Test
    public void testDetectEmbeddingType_openaiModel() {
        assertEquals(FunctionName.TEXT_EMBEDDING, MemoryModelService.detectEmbeddingType("text-embedding-3-small"));
    }

    @Test
    public void testDetectEmbeddingDimension_openaiModel() {
        assertEquals(Integer.valueOf(1536), MemoryModelService.detectEmbeddingDimension("text-embedding-3-small"));
        assertEquals(Integer.valueOf(3072), MemoryModelService.detectEmbeddingDimension("text-embedding-3-large"));
    }
}
