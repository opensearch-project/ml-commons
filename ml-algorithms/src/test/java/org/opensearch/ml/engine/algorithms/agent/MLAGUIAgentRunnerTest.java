/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CONTEXT;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.agent.MLAgent;

public class MLAGUIAgentRunnerTest {

    private MLAGUIAgentRunner agentRunner;
    private MLAgent mlAgent;

    @Before
    public void setUp() {
        // For context processing tests, we only need a basic instance
        // The processAGUIContext method doesn't use most constructor fields
        agentRunner = new MLAGUIAgentRunner(
            null, // client
            null, // settings
            null, // clusterService
            null, // xContentRegistry
            null, // toolFactories
            null, // memoryFactoryMap
            null, // sdkClient
            null  // encryptor
        );

        // Create a simple MLAgent instance (null is fine for context processing tests)
        mlAgent = null;
    }

    @Test
    public void testProcessAGUIContext_WithValidContext() {
        // Setup test data
        Map<String, String> params = new HashMap<>();
        String contextJson = "[\n"
            + "  {\n"
            + "    \"description\": \"Explore application page context\",\n"
            + "    \"value\": \"{\\\"appId\\\":\\\"explore\\\",\\\"timeRange\\\":{\\\"from\\\":\\\"now-15m\\\",\\\"to\\\":\\\"now\\\"}}\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"description\": \"User preferences\",\n"
            + "    \"value\": \"{\\\"theme\\\":\\\"dark\\\",\\\"language\\\":\\\"en\\\"}\"\n"
            + "  }\n"
            + "]";
        params.put("agui_context", contextJson);

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify context was processed and added to params
        assertTrue("Should contain context parameter", params.containsKey(CONTEXT));

        String processedContext = params.get(CONTEXT);
        assertTrue("Context should contain explore app description", processedContext.contains("Explore application page context"));
        assertTrue("Context should contain user preferences description", processedContext.contains("User preferences"));
        assertTrue("Context should contain explore app value", processedContext.contains("appId"));
        assertTrue("Context should contain user preferences value", processedContext.contains("theme"));
    }

    @Test
    public void testProcessAGUIContext_WithEmptyContext() {
        // Setup test data
        Map<String, String> params = new HashMap<>();
        String contextJson = "[]";
        params.put("agui_context", contextJson);

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify no context parameter was added for empty context
        assertFalse("Should not contain context parameter for empty context", params.containsKey(CONTEXT));
    }

    @Test
    public void testProcessAGUIContext_WithNoContext() {
        // Setup test data with no agui_context
        Map<String, String> params = new HashMap<>();

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify no context parameter was added when no agui_context
        assertFalse("Should not contain context parameter when no agui_context", params.containsKey(CONTEXT));
    }

    @Test
    public void testProcessAGUIContext_WithInvalidJson() {
        // Setup test data with invalid JSON
        Map<String, String> params = new HashMap<>();
        params.put("agui_context", "{invalid json}");

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify no context parameter was added for invalid JSON
        assertFalse("Should not contain context parameter for invalid JSON", params.containsKey(CONTEXT));
    }

    @Test
    public void testProcessAGUIContext_WithMissingFields() {
        // Setup test data with context items missing description or value
        Map<String, String> params = new HashMap<>();
        String contextJson = "[\n"
            + "  {\"description\": \"Only description\"},\n"
            + "  {\"value\": \"Only value\"},\n"
            + "  {\n"
            + "    \"description\": \"Complete item\",\n"
            + "    \"value\": \"Complete value\"\n"
            + "  }\n"
            + "]";
        params.put("agui_context", contextJson);

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify only the complete item was processed
        assertTrue("Should contain context parameter", params.containsKey(CONTEXT));

        String processedContext = params.get(CONTEXT);
        assertTrue("Context should contain complete item description", processedContext.contains("Complete item"));
        assertTrue("Context should contain complete item value", processedContext.contains("Complete value"));
        assertFalse("Context should not contain incomplete items", processedContext.contains("Only description"));
        assertFalse("Context should not contain incomplete items", processedContext.contains("Only value"));
    }

    @Test
    public void testProcessAGUIContext_RealWorldSample() {
        // Test with the actual conversation sample that was not working
        Map<String, String> params = new HashMap<>();
        String contextJson = "[\n"
            + "  {\n"
            + "    \"description\": \"Explore application page context\",\n"
            + "    \"value\": \"{\\\"appId\\\":\\\"explore\\\",\\\"timeRange\\\":{\\\"from\\\":\\\"now-15m\\\",\\\"to\\\":\\\"now\\\"},\\\"query\\\":{\\\"query\\\":\\\"\\\",\\\"language\\\":\\\"PPL\\\"}}\"\n"
            + "  },\n"
            + "  {\n"
            + "    \"description\": \"Selected text: \\\"hello\\\\n\\\\n\\\\nHello! How can I assist you today?\\\\n\\\\nwhat d...\\\"\",\n"
            + "    \"value\": \"hello\\\\n\\\\n\\\\nHello! How can I assist you today?\\\\n\\\\nwhat did i ask you previously\"\n"
            + "  }\n"
            + "]";
        params.put("agui_context", contextJson);

        // Use reflection to call the private method
        try {
            Method method = MLAGUIAgentRunner.class.getDeclaredMethod("processAGUIContext", MLAgent.class, Map.class);
            method.setAccessible(true);
            method.invoke(agentRunner, mlAgent, params);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke processAGUIContext", e);
        }

        // Verify context was processed correctly
        assertTrue("Should contain context parameter", params.containsKey(CONTEXT));

        String processedContext = params.get(CONTEXT);
        assertTrue("Context should contain explore app description", processedContext.contains("Explore application page context"));
        assertTrue("Context should contain selected text description", processedContext.contains("Selected text"));
        assertTrue("Context should contain app ID", processedContext.contains("appId"));
        assertTrue("Context should contain conversation text", processedContext.contains("hello"));

        // Verify the format is correct
        assertTrue("Context should be formatted with bullet points", processedContext.contains("- "));
    }
}
