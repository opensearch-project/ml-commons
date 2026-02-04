/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestOpenaiV1ChatCompletionsFunctionCallingIT extends RestBaseAgentToolsIT {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_KEY");
    private static final String OPENAI_MODEL_ID = "gpt-5";
    private String testIndex = "openai_test_index";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUpClusterSettings();
        createTestIndex();
    }

    private void createTestIndex() throws Exception {
        createIndexWithConfiguration(testIndex, "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
        addDocToIndex(testIndex, "1", List.of("name", "value"), List.of("test", "data"));
    }

    @After
    public void tearDown() throws Exception {
        restoreClusterSettings();
        super.tearDown();
    }

    private void setUpClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", true);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", true);
        updateClusterSettings("plugins.ml_commons.unified_agent_api_enabled", true);
    }

    private void restoreClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", null);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", null);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", null);
        updateClusterSettings("plugins.ml_commons.unified_agent_api_enabled", null);
    }

    // ========== NEW API TESTS (Simplified Agent Registration) ==========

    /**
     * Test 1: New API - Tool execution with simplified agent registration
     */
    public void testNewAPI_ToolExecutionWithFunctionCalling() throws IOException, ParseException {
        // Create agent with ListIndexTool using simplified agent registration
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"OpenAI Tool Test Agent\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for OpenAI function calling new interface with tools\",\n"
                    + "  \"model\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"model_provider\": \"openai/v1/chat/completions\",\n"
                    + "    \"credential\": {\n"
                    + "      \"openai_api_key\": \"%s\"\n"
                    + "    },\n"
                    + "    \"model_parameters\": {\n"
                    + "      \"max_iteration\": 5,\n"
                    + "      \"stop_when_no_tool_found\": true,\n"
                    + "      \"system_prompt\": \"You are a helpful assistant. Use tools when needed.\"\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"tools\": [\n"
                    + "    {\n"
                    + "      \"type\": \"ListIndexTool\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"memory\": {\n"
                    + "    \"type\": \"conversation_index\"\n"
                    + "  }\n"
                    + "}",
                OPENAI_MODEL_ID,
                OPENAI_API_KEY
            );

        String agentId = createAgent(agentBody);

        // Execute agent with new API format (input field)
        String executeBody = "{\n" + "  \"input\": \"List all the indices in this cluster\"\n" + "}";

        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));

        Map<String, Object> responseMap = parseResponseToMap(response);
        assertNotNull("Response should contain inference_results", responseMap.get("inference_results"));

        // Extract and verify the agent response contains the test index name
        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) responseMap.get("inference_results");
        assertNotNull("Inference results should not be null", inferenceResults);
        assertFalse("Inference results should not be empty", inferenceResults.isEmpty());

        Map<String, Object> firstResult = inferenceResults.get(0);
        List<Map<String, Object>> output = (List<Map<String, Object>>) firstResult.get("output");
        assertNotNull("Output should not be null", output);

        boolean foundResponse = false;
        for (Map<String, Object> outputItem : output) {
            String name = (String) outputItem.get("name");
            if ("response".equals(name)) {
                Map<String, Object> dataAsMap = (Map<String, Object>) outputItem.get("dataAsMap");
                if (dataAsMap != null) {
                    String agentResponse = (String) dataAsMap.get("response");
                    assertNotNull("Agent response should not be null", agentResponse);
                    assertTrue("Agent response should contain the test index name: " + testIndex, agentResponse.contains(testIndex));
                    foundResponse = true;
                    break;
                }
            }
        }
        assertTrue("Should find response in output", foundResponse);

        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
}
