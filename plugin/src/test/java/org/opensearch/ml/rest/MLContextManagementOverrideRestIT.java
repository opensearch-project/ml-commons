/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for context management with conversational agent registration.
 * Tests that context management works correctly with conversational agents, which support hooks.
 * Note: Flow agents do not support context management hooks at this time.
 */
public class MLContextManagementOverrideRestIT extends MLCommonsRestTestCase {

    private String templateName;
    private static final String TEST_MODEL_ID = "test_model_id_for_registration";

    @Before
    public void setup() throws IOException {
        // Enable agent framework
        updateClusterSettings("plugins.ml_commons.agent_framework_enabled", true);

        // Create a context management template
        templateName = "test_template_" + System.currentTimeMillis();

        String templateBody = "{\n"
            + "  \"name\": \""
            + templateName
            + "\",\n"
            + "  \"description\": \"Test template for integration test\",\n"
            + "  \"hooks\": {\n"
            + "    \"POST_TOOL\": [\n"
            + "      {\n"
            + "        \"type\": \"ToolsOutputTruncateManager\",\n"
            + "        \"config\": {\n"
            + "          \"max_output_length\": 10\n"
            + "        }\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
            + "}";

        Response createTemplateResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/context_management/" + templateName,
                null,
                TestHelper.toHttpEntity(templateBody),
                null
            );
        assertEquals(200, createTemplateResponse.getStatusLine().getStatusCode());
    }

    @After
    public void cleanup() throws IOException {
        // Delete template
        if (templateName != null) {
            try {
                TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/context_management/" + templateName, null, "", null);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Test: Conversational agent can be registered with context_management_name reference
     */
    @Test
    public void testConversationalAgentRegistrationWithContextManagementNameReference() throws IOException {
        // Register conversational agent with template reference
        String registerAgentBody = "{\n"
            + "  \"name\": \"Test_Conversational_Agent_With_Context_Ref\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent with context management reference\",\n"
            + "  \"context_management_name\": \""
            + templateName
            + "\",\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + TEST_MODEL_ID
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"max_iteration\": 5\n"
            + "    }\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  },\n"
            + "  \"tools\": []\n"
            + "}";

        Response registerResponse = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentBody), null);

        assertEquals(200, registerResponse.getStatusLine().getStatusCode());

        Map<String, Object> registerMap = parseResponseToMap(registerResponse);
        String agentId = (String) registerMap.get("agent_id");
        assertNotNull("Agent ID should not be null", agentId);

        // Verify agent was registered with correct context_management_name
        Response getAgentResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);

        Map<String, Object> agentMap = parseResponseToMap(getAgentResponse);
        assertEquals("Agent should have context_management_name", templateName, agentMap.get("context_management_name"));
        assertEquals("Agent type should be conversational", "conversational", agentMap.get("type"));

        // Cleanup agent
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }

    /**
     * Test: Conversational agent can be registered with inline context_management
     */
    @Test
    public void testConversationalAgentRegistrationWithInlineContextManagement() throws IOException {
        // Register conversational agent with inline context_management
        String registerAgentBody = "{\n"
            + "  \"name\": \"Test_Conversational_Agent_With_Inline_Context\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent with inline context management\",\n"
            + "  \"context_management\": {\n"
            + "    \"name\": \"inline_template\",\n"
            + "    \"description\": \"Inline template definition\",\n"
            + "    \"hooks\": {\n"
            + "      \"POST_TOOL\": [\n"
            + "        {\n"
            + "          \"type\": \"ToolsOutputTruncateManager\",\n"
            + "          \"config\": {\n"
            + "            \"max_output_length\": 50\n"
            + "          }\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  },\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + TEST_MODEL_ID
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"max_iteration\": 5\n"
            + "    }\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  },\n"
            + "  \"tools\": []\n"
            + "}";

        Response registerResponse = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentBody), null);

        assertEquals(200, registerResponse.getStatusLine().getStatusCode());

        Map<String, Object> registerMap = parseResponseToMap(registerResponse);
        String agentId = (String) registerMap.get("agent_id");
        assertNotNull("Agent ID should not be null", agentId);

        // Verify agent was registered with inline context_management
        Response getAgentResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);

        Map<String, Object> agentMap = parseResponseToMap(getAgentResponse);
        assertNotNull("Agent should have context_management", agentMap.get("context_management"));
        assertEquals("Agent type should be conversational", "conversational", agentMap.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contextMgmt = (Map<String, Object>) agentMap.get("context_management");
        assertEquals("inline_template", contextMgmt.get("name"));

        // Cleanup agent
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }

    /**
     * Test: Agent registration fails when both context_management fields are specified
     */
    @Test
    public void testConversationalAgentRegistration_BothContextManagementFields_ShouldFail() throws IOException {
        String registerAgentBody = "{\n"
            + "  \"name\": \"Test_Conversational_Agent_Invalid\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent with both context management fields\",\n"
            + "  \"context_management_name\": \""
            + templateName
            + "\",\n"
            + "  \"context_management\": {\n"
            + "    \"name\": \"inline_template\",\n"
            + "    \"hooks\": {}\n"
            + "  },\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + TEST_MODEL_ID
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"max_iteration\": 5\n"
            + "    }\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  },\n"
            + "  \"tools\": []\n"
            + "}";

        try {
            Response registerResponse = TestHelper
                .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentBody), null);

            fail("Should have thrown exception for specifying both context_management_name and context_management");
        } catch (Exception e) {
            // Expected to fail - should contain validation error message
            String errorMessage = e.getMessage().toLowerCase();
            assertTrue(
                "Error message should mention 'cannot specify both': " + errorMessage,
                errorMessage.contains("cannot specify both") || errorMessage.contains("illegal")
            );
        }
    }

    /**
     * Test: Agent registration fails when referencing non-existent template
     */
    @Test
    public void testConversationalAgentRegistration_NonExistentTemplate_ShouldFail() throws IOException {
        String nonExistentTemplate = "non_existent_template_" + System.currentTimeMillis();

        String registerAgentBody = "{\n"
            + "  \"name\": \"Test_Conversational_Agent_Invalid_Template\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent with non-existent template\",\n"
            + "  \"context_management_name\": \""
            + nonExistentTemplate
            + "\",\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + TEST_MODEL_ID
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"max_iteration\": 5\n"
            + "    }\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  },\n"
            + "  \"tools\": []\n"
            + "}";

        try {
            Response registerResponse = TestHelper
                .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentBody), null);

            fail("Should have thrown exception for non-existent context management template");
        } catch (Exception e) {
            // Expected to fail - template not found
            String errorMessage = e.getMessage().toLowerCase();
            assertTrue(
                "Error message should indicate resource not found: " + errorMessage,
                errorMessage.contains("not found") || errorMessage.contains("does not exist") || errorMessage.contains("resourcenotfound")
            );
        }
    }
}
