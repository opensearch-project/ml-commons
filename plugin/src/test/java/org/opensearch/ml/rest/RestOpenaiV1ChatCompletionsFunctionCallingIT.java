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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestOpenaiV1ChatCompletionsFunctionCallingIT extends RestBaseAgentToolsIT {

    private static final String OPENAI_API_KEY = System.getenv("OPENAI_KEY");
    private static final String OPENAI_MODEL_ID = "gpt-4o";
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
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", null);
        updateClusterSettings("plugins.ml_commons.unified_agent_api_enabled", null);
    }

    private String createOpenAIConnector() throws IOException, InterruptedException {
        String connectorEntity = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"OpenAI Chat Connector\",\n"
                    + "  \"description\": \"Connector for OpenAI chat completions with function calling\",\n"
                    + "  \"version\": \"1\",\n"
                    + "  \"protocol\": \"http\",\n"
                    + "  \"parameters\": {\n"
                    + "    \"model\": \"%s\"\n"
                    + "  },\n"
                    + "  \"credential\": {\n"
                    + "    \"openai_api_key\": \"%s\"\n"
                    + "  },\n"
                    + "  \"actions\": [\n"
                    + "    {\n"
                    + "      \"action_type\": \"predict\",\n"
                    + "      \"method\": \"POST\",\n"
                    + "      \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
                    + "      \"headers\": {\n"
                    + "        \"Authorization\": \"Bearer ${credential.openai_api_key}\",\n"
                    + "        \"Content-Type\": \"application/json\"\n"
                    + "      },\n"
                    + "      \"request_body\": \"${parameters.request_body}\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"client_config\": {\n"
                    + "    \"max_connection\": 20,\n"
                    + "    \"connection_timeout\": 50,\n"
                    + "    \"read_timeout\": 50\n"
                    + "  }\n"
                    + "}",
                OPENAI_MODEL_ID,
                OPENAI_API_KEY
            );
        return registerConnector(connectorEntity);
    }

    // ========== NEW API TESTS (Function Calling with Explicit Connector) ==========

    /**
     * Test 1: Tool execution with function calling using explicit connector for timeout control
     */
    @Test
    public void testNewAPI_ToolExecutionWithFunctionCalling() throws IOException, ParseException, InterruptedException {
        Assume.assumeNotNull(OPENAI_API_KEY);

        String connectorId = createOpenAIConnector();

        Response registerResponse = RestMLRemoteInferenceIT.registerRemoteModel("openai_chat_model", "openai_chat_model", connectorId);
        Map<String, Object> registerMap = parseResponseToMap(registerResponse);
        String modelId = (String) registerMap.get("model_id");

        Response deployResponse = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/models/" + modelId + "/_deploy", null, (String) null, null);
        Map<String, Object> deployMap = parseResponseToMap(deployResponse);
        String taskId = (String) deployMap.get("task_id");
        waitForTask(taskId, MLTaskState.COMPLETED);

        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"OpenAI Tool Test Agent\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for OpenAI function calling with tools\",\n"
                    + "  \"llm\": {\n"
                    + "    \"model_id\": \"%s\"\n"
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
                modelId
            );

        String agentId = createAgent(agentBody);

        String executeBody = "{\n" + "  \"input\": \"List all the indices in this cluster\"\n" + "}";

        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));

        Map<String, Object> responseMap = parseResponseToMap(response);
        assertNotNull("Response should contain inference_results", responseMap.get("inference_results"));

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

        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
}
