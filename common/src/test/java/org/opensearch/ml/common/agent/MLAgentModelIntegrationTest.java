/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MLAgentModelIntegrationTest {

    @Test
    public void testMLAgentWithModelSpec() {
        Map<String, String> credential = new HashMap<>();
        credential.put("access_key", "test_key");
        credential.put("secret_key", "test_secret");
        
        MLAgentModelSpec modelSpec = MLAgentModelSpec.builder()
            .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
            .modelProvider("bedrock/converse")
            .credential(credential)
            .build();

        MLAgent agent = MLAgent
            .builder()
            .name("Test Agent")
            .type("conversational")
            .description("Test agent with model spec")
            .model(modelSpec)
            .build();

        // Verify the agent has the model spec
        assertNotNull(agent.getModel());
        assertEquals("us.anthropic.claude-3-7-sonnet-20250219-v1:0", agent.getModel().getModelId());
        assertEquals("bedrock/converse", agent.getModel().getModelProvider());
        assertNull(agent.getLlm()); // Should not have LLM spec yet
    }

    @Test
    public void testMLAgentWithLLMSpec() {
        LLMSpec llmSpec = LLMSpec.builder().modelId("existing-model-id").build();

        MLAgent agent = MLAgent
            .builder()
            .name("Legacy Agent")
            .type("conversational")
            .description("Legacy agent with LLM spec")
            .llm(llmSpec)
            .build();

        // Verify the agent has the LLM spec
        assertNotNull(agent.getLlm());
        assertEquals("existing-model-id", agent.getLlm().getModelId());
        assertNull(agent.getModel()); // Should not have model spec
    }
}
