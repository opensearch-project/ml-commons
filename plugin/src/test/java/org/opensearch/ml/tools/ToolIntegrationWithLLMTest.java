/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.tools;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.rest.RestBaseAgentToolsIT;
import org.opensearch.ml.utils.TestHelper;

import com.sun.net.httpserver.HttpServer;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

@Log4j2
public abstract class ToolIntegrationWithLLMTest extends RestBaseAgentToolsIT {

    private int MAX_RETRIES;
    private static final int DEFAULT_TASK_RESULT_QUERY_INTERVAL_IN_MILLISECOND = 1000;

    protected HttpServer server;
    protected String modelId;
    protected String agentId;
    protected String modelGroupId;
    protected String connectorId;

    abstract List<PromptHandler> promptHandlers();

    abstract String toolType();

    @Before
    public void setupTestChatAgent() throws IOException, InterruptedException {
        server = MockLLM.setupMockLLM(promptHandlers());
        server.start();
        setUpClusterSettings();
        connectorId = setUpConnectorWithRetry(5);
        setupModelGroup();
        setupLLMModel(connectorId);
        // wait for model to get deployed
        TimeUnit.SECONDS.sleep(1);
        setupConversationalAgent(modelId);
        log.info("model_id: {}, agent_id: {}", modelId, agentId);
        MAX_RETRIES = this.getClusterHosts().size();
    }

    @After
    public void cleanUpClusterSetting() throws IOException {
        restoreClusterSettings();
    }

    @After
    public void stopMockLLM() {
        server.stop(1);
    }

    @After
    public void deleteModel() throws IOException {
        undeployModel(modelId);
        checkForModelUndeployedStatus(modelId);
        deleteModel(client(), modelId, null);
    }

    @SneakyThrows
    private void checkForModelUndeployedStatus(String modelId) {
        Predicate<String> condition = responseStr -> {
            try {
                Map<String, Object> responseInMap = StringUtils.gson.fromJson(responseStr, Map.class);
                MLModelState state = MLModelState.from(responseInMap.get(MLModel.MODEL_STATE_FIELD).toString());
                return MLModelState.UNDEPLOYED.equals(state);
            } catch (Exception e) {
                return false;
            }
        };
        waitResponseMeetingCondition("GET", "/_plugins/_ml/models/" + modelId, null, condition);
    }

    @SneakyThrows
    protected Response waitResponseMeetingCondition(String method, String endpoint, String jsonEntity, Predicate<String> condition) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            Response response = TestHelper.makeRequest(client(), method, endpoint, null, jsonEntity, null);
            assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
            String entityString = TestHelper.httpEntityToString(response.getEntity());
            if (condition.test(entityString)) {
                return response;
            }
            logger
                .info(
                    "The {}-th attempt on {}:{} . response: {}, responseBody: {}",
                    attempt,
                    method,
                    endpoint,
                    response.toString(),
                    entityString
                );
            Thread.sleep(DEFAULT_TASK_RESULT_QUERY_INTERVAL_IN_MILLISECOND);
        }
        fail(
            String
                .format(
                    Locale.ROOT,
                    "The response failed to meet condition after %d attempts. Attempted to perform %s : %s",
                    MAX_RETRIES,
                    method,
                    endpoint
                )
        );
        return null;
    }

    private String setUpConnectorWithRetry(int maxRetryTimes) throws InterruptedException {
        int retryTimes = 0;
        String connectorId = null;
        while (retryTimes < maxRetryTimes) {
            try {
                connectorId = setUpConnector();
                break;
            } catch (Exception e) {
                // Wait for ML encryption master key has been initialized
                log.info("Failed to setup connector, retry times: {}", retryTimes);
                retryTimes++;
                TimeUnit.SECONDS.sleep(20);
            }
        }
        return connectorId;
    }

    private String setUpConnector() throws IOException {
        String url = String.format(Locale.ROOT, "http://127.0.0.1:%d/invoke", server.getAddress().getPort());
        return createConnector(
            "{\n"
                + " \"name\": \"BedRock test claude Connector\",\n"
                + " \"description\": \"The connector to BedRock service for claude model\",\n"
                + " \"version\": 1,\n"
                + " \"protocol\": \"aws_sigv4\",\n"
                + " \"parameters\": {\n"
                + "  \"region\": \"us-east-1\",\n"
                + "  \"service_name\": \"bedrock\",\n"
                + "  \"anthropic_version\": \"bedrock-2023-05-31\",\n"
                + "  \"endpoint\": \"bedrock.us-east-1.amazonaws.com\",\n"
                + "  \"auth\": \"Sig_V4\",\n"
                + "  \"content_type\": \"application/json\",\n"
                + "  \"max_tokens_to_sample\": 8000,\n"
                + "  \"temperature\": 0.0001,\n"
                + "  \"response_filter\": \"$.completion\"\n"
                + " },\n"
                + " \"credential\": {\n"
                + "  \"access_key\": \"<key>\",\n"
                + "  \"secret_key\": \"<secret>\"\n"
                + " },\n"
                + " \"actions\": [\n"
                + "  {\n"
                + "   \"action_type\": \"predict\",\n"
                + "   \"method\": \"POST\",\n"
                + "   \"url\": \""
                + url
                + "\",\n"
                + "   \"headers\": {\n"
                + "    \"content-type\": \"application/json\",\n"
                + "    \"x-amz-content-sha256\": \"required\"\n"
                + "   },\n"
                + "   \"request_body\": \"{\\\"prompt\\\":\\\"${parameters.prompt}\\\", \\\"max_tokens_to_sample\\\":${parameters.max_tokens_to_sample}, \\\"temperature\\\":${parameters.temperature},  \\\"anthropic_version\\\":\\\"${parameters.anthropic_version}\\\" }\"\n"
                + "  }\n"
                + " ]\n"
                + "}"
        );
    }

    private void setUpClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", true);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
    }

    private void restoreClusterSettings() throws IOException {
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", null);
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", null);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", null);
    }

    protected String createConnector(String requestBody) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/connectors/_create", null, requestBody, null);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
        return parseFieldFromResponse(response, MLModel.CONNECTOR_ID_FIELD).toString();
    }

    private void setupModelGroup() throws IOException {
        String input = "{\n"
            + "    \"name\": \"test_model_group_bedrock-"
            + UUID.randomUUID()
            + "\",\n"
            + "    \"description\": \"This is a public model group\"\n"
            + "}";

        registerModelGroup(client(), input, response -> modelGroupId = (String) response.get("model_group_id"));
    }

    private void setupLLMModel(String connectorId) throws IOException {
        String input = "{\n"
            + "    \"name\": \"Bedrock Claude V2 model\",\n"
            + "    \"function_name\": \"remote\",\n"
            + "    \"model_group_id\": \""
            + modelGroupId
            + "\",\n"
            + "    \"description\": \"test model\",\n"
            + "    \"connector_id\": \""
            + connectorId
            + "\",\n"
            + "  \"interface\": {\n"
            + "    \"input\": {},\n"
            + "    \"output\": {}\n"
            + "    }\n"
            + "}";

        registerModel(client(), input, response -> {
            modelId = (String) response.get("model_id");
            try {
                deployModel(modelId);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }

    private void setupConversationalAgent(String modelId) throws IOException {
        String input = "{\n"
            + "  \"name\": \"integTest-agent\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"this is a test agent\",\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + modelId
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"max_iteration\": \"5\",\n"
            + "      \"stop_when_no_tool_found\": \"true\",\n"
            + "      \"response_filter\": \"$.completion\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \""
            + toolType()
            + "\",\n"
            + "      \"name\": \""
            + toolType()
            + "\",\n"
            + "      \"include_output_in_agent_response\": true,\n"
            + "      \"description\": \"tool description\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  }\n"
            + "}";

        registerMLAgent(client(), input, response -> agentId = (String) response.get("agent_id"));
    }
}
