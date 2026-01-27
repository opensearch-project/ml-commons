/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RestGeminiFunctionCallingIT extends RestBaseAgentToolsIT {

    // TODO: Configure API key in GitHub secrets before enabling tests
    // private static final String GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE";
    private static final String GEMINI_API_KEY = "PLACEHOLDER_API_KEY";
    private static final String GEMINI_MODEL_ID = "gemini-2.0-flash-exp";
    private static final int MAX_RETRIES = 5;
    private static final int DEFAULT_TASK_RESULT_QUERY_INTERVAL_IN_MILLISECOND = 1000;

    private String modelId;
    private String modelGroupId;
    private String connectorId;
    private String testIndex = "gemini_test_index";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUpClusterSettings();
        // Setup connector and model for old API tests
        connectorId = setUpConnectorWithRetry(5);
        setupModelGroup();
        setupLLMModel(connectorId);
        // Wait for model to get deployed
        TimeUnit.SECONDS.sleep(2);
        log.info("Setup complete - connector_id: {}, model_id: {}", connectorId, modelId);
    }

    @After
    public void tearDown() throws Exception {
        restoreClusterSettings();
        if (modelId != null) {
            try {
                undeployModel(modelId);
                checkForModelUndeployedStatus(modelId);
                deleteModel(client(), modelId, null);
            } catch (Exception e) {
                log.warn("Failed to cleanup model: {}", modelId, e);
            }
        }
        super.tearDown();
    }

    private void setUpClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", true);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", true);
    }

    private void restoreClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", null);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", null);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", null);
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", null);
    }

    private String setUpConnectorWithRetry(int maxRetryTimes) throws InterruptedException {
        int retryTimes = 0;
        String connectorId = null;
        while (retryTimes < maxRetryTimes) {
            try {
                connectorId = setUpConnector();
                break;
            } catch (Exception e) {
                log.info("Failed to setup connector, retry times: {}", retryTimes, e);
                retryTimes++;
                TimeUnit.SECONDS.sleep(10);
            }
        }
        return connectorId;
    }

    private String setUpConnector() throws IOException, ParseException {
        // Connector for old API - uses user_prompt parameter
        String connectorBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Test Connector\",\n"
                    + "  \"description\": \"Connector for Gemini API with function calling\",\n"
                    + "  \"version\": 1,\n"
                    + "  \"protocol\": \"http\",\n"
                    + "  \"parameters\": {\n"
                    + "    \"model\": \"%s\"\n"
                    + "  },\n"
                    + "  \"credential\": {\n"
                    + "    \"gemini_api_key\": \"%s\"\n"
                    + "  },\n"
                    + "  \"actions\": [\n"
                    + "    {\n"
                    + "      \"action_type\": \"predict\",\n"
                    + "      \"method\": \"POST\",\n"
                    + "      \"url\": \"https://generativelanguage.googleapis.com/v1beta/models/${parameters.model}:generateContent\",\n"
                    + "      \"headers\": {\n"
                    + "        \"Content-Type\": \"application/json\",\n"
                    + "        \"x-goog-api-key\": \"${credential.gemini_api_key}\"\n"
                    + "      },\n"
                    + "      \"request_body\": \"{\\\"systemInstruction\\\":{\\\"parts\\\":[{\\\"text\\\":\\\"${parameters.system_prompt:-You are a helpful assistant.}\\\"}]},\\\"contents\\\":[${parameters._chat_history:-}{\\\"role\\\":\\\"user\\\",\\\"parts\\\":[{\\\"text\\\":\\\"${parameters.user_prompt}\\\"}]}${parameters._interactions:-}]${parameters.tool_configs:-}}\"\n"
                    + "    }\n"
                    + "  ]\n"
                    + "}",
                GEMINI_MODEL_ID,
                GEMINI_API_KEY
            );

        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, connectorBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
        Map<String, Object> responseMap = parseResponseToMap(response);
        return (String) responseMap.get("connector_id");
    }

    private void setupModelGroup() throws IOException {
        String input = String
            .format(
                Locale.ROOT,
                "{\n" + "    \"name\": \"test_gemini_model_group_%s\",\n" + "    \"description\": \"Test model group for Gemini\"\n" + "}",
                UUID.randomUUID()
            );
        registerModelGroup(client(), input, response -> modelGroupId = (String) response.get("model_group_id"));
    }

    private void setupLLMModel(String connectorId) throws IOException {
        String input = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "    \"name\": \"Gemini Flash Model\",\n"
                    + "    \"function_name\": \"remote\",\n"
                    + "    \"model_group_id\": \"%s\",\n"
                    + "    \"description\": \"Test Gemini model\",\n"
                    + "    \"connector_id\": \"%s\",\n"
                    + "    \"interface\": {\n"
                    + "        \"input\": {},\n"
                    + "        \"output\": {}\n"
                    + "    }\n"
                    + "}",
                modelGroupId,
                connectorId
            );

        registerModel(client(), input, response -> {
            modelId = (String) response.get("model_id");
            try {
                deployModel(modelId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void checkForModelUndeployedStatus(String modelId) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + modelId, null, "", null);
                String entityString = TestHelper.httpEntityToString(response.getEntity());
                Map<String, Object> responseInMap = StringUtils.gson.fromJson(entityString, Map.class);
                MLModelState state = MLModelState.from(responseInMap.get(MLModel.MODEL_STATE_FIELD).toString());
                if (MLModelState.UNDEPLOYED.equals(state)) {
                    return;
                }
                Thread.sleep(DEFAULT_TASK_RESULT_QUERY_INTERVAL_IN_MILLISECOND);
            } catch (Exception e) {
                log.warn("Failed to check model status on attempt {}", attempt, e);
            }
        }
    }

    // ========== NEW API TESTS (Simplified Agent Registration) ==========

    /**
     * Test 1: New API - Tool execution with simplified agent registration
     * TODO: Uncomment when GitHub secrets are configured with GEMINI_API_KEY
     */
    /* DISABLED - Waiting for GitHub secrets configuration
    public void testNewAPI_ToolExecutionWithFunctionCalling() throws IOException, ParseException {
        log.info("=== Test 1 (New API): Tool Execution with Function Calling ===");
    
        // Enable simplified agent registration
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", true);
    
        // Create agent with ListIndexTool using simplified agent registration
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Tool Test Agent\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for Gemini function calling with tools\",\n"
                    + "  \"model\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"model_provider\": \"gemini/generatecontent\",\n"
                    + "    \"credential\": {\n"
                    + "      \"gemini_api_key\": \"%s\"\n"
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
                GEMINI_MODEL_ID,
                GEMINI_API_KEY
            );
    
        String agentId = createAgent(agentBody);
        log.info("Created agent with ID: {}", agentId);
    
        // Execute agent with new API format (input field)
        String executeBody = "{\n" + "  \"input\": \"List all the indices in this cluster\"\n" + "}";
    
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        log.info("Agent execution response: {}", responseStr);
    
        Map<String, Object> responseMap = parseResponseToMap(response);
        assertNotNull("Response should contain inference_results", responseMap.get("inference_results"));
    
        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
    */

    /**
     * Test 2: New API - Agent execution with SearchIndexTool
     * TODO: Uncomment when GitHub secrets are configured with GEMINI_API_KEY
     */
    /* DISABLED - Waiting for GitHub secrets configuration
    public void testNewAPI_AgentExecutionWithSearchIndexTool() throws Exception {
        log.info("=== Test 2 (New API): Agent Execution with SearchIndexTool ===");
    
        // Enable simplified agent registration
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", true);
    
        // Create test index with data
        createIndexWithConfiguration(testIndex, "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
        addDocToIndex(testIndex, "1", List.of("name", "value"), List.of("test", "data"));
    
        // Create agent with SearchIndexTool using simplified agent registration
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Search Agent\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for Gemini with search tool\",\n"
                    + "  \"model\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"model_provider\": \"gemini/generatecontent\",\n"
                    + "    \"credential\": {\n"
                    + "      \"gemini_api_key\": \"%s\"\n"
                    + "    },\n"
                    + "    \"model_parameters\": {\n"
                    + "      \"max_iteration\": 5,\n"
                    + "      \"stop_when_no_tool_found\": true,\n"
                    + "      \"system_prompt\": \"You are a helpful assistant. Use search tools to find information.\"\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"tools\": [\n"
                    + "    {\n"
                    + "      \"type\": \"SearchIndexTool\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"memory\": {\n"
                    + "    \"type\": \"conversation_index\"\n"
                    + "  }\n"
                    + "}",
                GEMINI_MODEL_ID,
                GEMINI_API_KEY
            );
    
        String agentId = createAgent(agentBody);
        log.info("Created agent with ID: {}", agentId);
    
        // Execute agent with new API format
        String executeBody = String
            .format(Locale.ROOT, "{\n" + "  \"input\": \"Search the %s index for documents with name=test\"\n" + "}", testIndex);
    
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        log.info("Search agent execution response: {}", responseStr);
    
        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
    */

    /**
     * Test 3: New API - Multimodal input with text and image
     * TODO: Uncomment when GitHub secrets are configured with GEMINI_API_KEY
     */
    /* DISABLED - Waiting for GitHub secrets configuration
    public void testNewAPI_MultimodalInput() throws IOException, ParseException {
        log.info("=== Test 3 (New API): Multimodal Input Test ===");
    
        // Enable simplified agent registration
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", true);
    
        // Create agent for multimodal using simplified agent registration (with tools to avoid empty tools array error)
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Multimodal Agent\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for Gemini multimodal capabilities\",\n"
                    + "  \"model\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"model_provider\": \"gemini/generatecontent\",\n"
                    + "    \"credential\": {\n"
                    + "      \"gemini_api_key\": \"%s\"\n"
                    + "    },\n"
                    + "    \"model_parameters\": {\n"
                    + "      \"system_prompt\": \"You are a helpful assistant that can understand images and text.\"\n"
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
                GEMINI_MODEL_ID,
                GEMINI_API_KEY
            );
    
        String agentId = createAgent(agentBody);
        log.info("Created multimodal agent with ID: {}", agentId);
    
        // Test multimodal input with text-only first (array format)
        // Note: Full multimodal with image may require additional code fixes for tools handling
        String executeBody = "{\n"
            + "  \"input\": [\n"
            + "    {\n"
            + "      \"type\": \"text\",\n"
            + "      \"text\": \"Hello, this is a test of multimodal input format. Please respond briefly.\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
    
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        log.info("Multimodal agent execution response: {}", responseStr);
    
        Map<String, Object> responseMap = parseResponseToMap(response);
        assertNotNull("Response should contain inference_results", responseMap.get("inference_results"));
    
        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
    */

    // ========== OLD API TESTS (Traditional Flow) ==========

    /**
     * Test 4: Old API - Tool execution with traditional flow
     * TODO: Uncomment when GitHub secrets are configured with GEMINI_API_KEY
     */
    /* DISABLED - Waiting for GitHub secrets configuration
    public void testOldAPI_ToolExecutionWithFunctionCalling() throws IOException, ParseException {
        log.info("=== Test 4 (Old API): Tool Execution with Function Calling ===");
    
        // Disable simplified agent registration for old API
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", false);
    
        // Create agent with ListIndexTool using old API (llm field)
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Tool Test Agent (Old API)\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for Gemini function calling with tools (old API)\",\n"
                    + "  \"llm\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"parameters\": {\n"
                    + "      \"max_iteration\": 5,\n"
                    + "      \"stop_when_no_tool_found\": true\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"tools\": [\n"
                    + "    {\n"
                    + "      \"type\": \"ListIndexTool\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"memory\": {\n"
                    + "    \"type\": \"conversation_index\"\n"
                    + "  },\n"
                    + "  \"parameters\": {\n"
                    + "    \"_llm_interface\": \"gemini/v1beta/generatecontent\"\n"
                    + "  }\n"
                    + "}",
                modelId
            );
    
        String agentId = createAgent(agentBody);
        log.info("Created agent with ID: {}", agentId);
    
        // Execute agent with old API format (parameters field)
        String executeBody = "{\n"
            + "  \"parameters\": {\n"
            + "    \"user_prompt\": \"List all the indices in this cluster\",\n"
            + "    \"question\": \"List all the indices in this cluster\"\n"
            + "  }\n"
            + "}";
    
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        log.info("Agent execution response: {}", responseStr);
    
        Map<String, Object> responseMap = parseResponseToMap(response);
        assertNotNull("Response should contain inference_results", responseMap.get("inference_results"));
    
        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
    */

    /**
     * Test 5: Old API - Agent execution with SearchIndexTool
     * TODO: Uncomment when GitHub secrets are configured with GEMINI_API_KEY
     */
    /* DISABLED - Waiting for GitHub secrets configuration
    public void testOldAPI_AgentExecutionWithSearchIndexTool() throws Exception {
        log.info("=== Test 5 (Old API): Agent Execution with SearchIndexTool ===");
    
        // Disable simplified agent registration for old API
        updateClusterSettings("plugins.ml_commons.simplified_agent_registration_enabled", false);
    
        // Create test index with data
        createIndexWithConfiguration(testIndex, "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0}}");
        addDocToIndex(testIndex, "1", List.of("name", "value"), List.of("test", "data"));
    
        // Create agent with SearchIndexTool using old API
        String agentBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"name\": \"Gemini Search Agent (Old API)\",\n"
                    + "  \"type\": \"conversational\",\n"
                    + "  \"description\": \"Test agent for Gemini with search tool (old API)\",\n"
                    + "  \"llm\": {\n"
                    + "    \"model_id\": \"%s\",\n"
                    + "    \"parameters\": {\n"
                    + "      \"max_iteration\": 5,\n"
                    + "      \"stop_when_no_tool_found\": true\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"tools\": [\n"
                    + "    {\n"
                    + "      \"type\": \"SearchIndexTool\"\n"
                    + "    }\n"
                    + "  ],\n"
                    + "  \"memory\": {\n"
                    + "    \"type\": \"conversation_index\"\n"
                    + "  },\n"
                    + "  \"parameters\": {\n"
                    + "    \"_llm_interface\": \"gemini/v1beta/generatecontent\"\n"
                    + "  }\n"
                    + "}",
                modelId
            );
    
        String agentId = createAgent(agentBody);
        log.info("Created agent with ID: {}", agentId);
    
        // Execute agent with old API format
        String executeBody = String
            .format(
                Locale.ROOT,
                "{\n"
                    + "  \"parameters\": {\n"
                    + "    \"user_prompt\": \"Search the %s index for documents with name=test\",\n"
                    + "    \"question\": \"Search the %s index for documents with name=test\"\n"
                    + "  }\n"
                    + "}",
                testIndex,
                testIndex
            );
    
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, executeBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
    
        String responseStr = TestHelper.httpEntityToString(response.getEntity());
        log.info("Search agent execution response: {}", responseStr);
    
        // Cleanup
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
    }
    */
}
