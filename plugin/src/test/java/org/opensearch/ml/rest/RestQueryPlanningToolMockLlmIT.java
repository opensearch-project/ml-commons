/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Integration tests for {@code QueryPlanningTool} against a mock OpenAI chat completions server in the test JVM,
 * so they run without an API key. The mock always answers with a chat completion whose message content is
 * {@link #GENERATED_QUERY}.
 */
@SuppressWarnings("unchecked")
public class RestQueryPlanningToolMockLlmIT extends MLCommonsRestTestCase {

    private static final Gson JSON = StringUtils.gson;
    private static final String INDEX = "qpt_mock_products";
    private static final String GENERATED_QUERY = "{\"query\":{\"match\":{\"name\":\"shoes\"}}}";

    private HttpServer mockLlm;
    private String modelId;

    @Before
    public void setUpMockLlm() throws Exception {
        Assume.assumeFalse("Containerized node cannot reach the test-JVM loopback mock LLM", isContainerizedCluster());
        mockLlm = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        mockLlm.createContext("/", this::handleLlmRequest);
        mockLlm.start();

        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", true);

        TestHelper.makeRequest(client(), "PUT", "/" + INDEX, null, "{\"mappings\":{\"properties\":{\"name\":{\"type\":\"text\"}}}}", null);
        TestHelper.makeRequest(client(), "POST", "/" + INDEX + "/_doc?refresh=true", null, "{\"name\":\"red running shoes\"}", null);

        String connectorId = registerConnector(openaiConnector());
        Response response = RestMLRemoteInferenceIT.registerRemoteModel("qpt_mock_" + UUID.randomUUID(), "qpt_mock", connectorId);
        modelId = (String) parseResponseToMap(response).get("model_id");
        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        waitForTask((String) parseResponseToMap(response).get("task_id"), MLTaskState.COMPLETED);
    }

    @After
    public void tearDownMockLlm() throws IOException {
        if (isContainerizedCluster()) {
            return;
        }
        if (mockLlm != null) {
            mockLlm.stop(0);
        }
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", null);
        updateClusterSettings("plugins.ml_commons.connector.private_ip_enabled", null);
        deleteIndex(INDEX);
    }

    public void testWithResponseFilter_ReturnsGeneratedQuery() throws IOException {
        String agentId = registerAgent("$.choices[0].message.content");
        Response response = execute(agentId);
        Map<String, Object> result = parseResponseToMap(response);
        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) result.get("inference_results");
        List<Map<String, Object>> output = (List<Map<String, Object>>) inferenceResults.get(0).get("output");
        assertEquals(JSON.fromJson(GENERATED_QUERY, Map.class), JSON.fromJson((String) output.get(0).get("result"), Map.class));
    }

    public void testWithoutResponseFilter_RejectsResponseEnvelope() throws IOException {
        String agentId = registerAgent(null);
        ResponseException e = expectThrows(ResponseException.class, () -> execute(agentId));
        assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        String body = TestHelper.httpEntityToString(e.getResponse().getEntity());
        assertTrue(body, body.contains("The model output is not an OpenSearch query"));
        assertTrue(body, body.contains("response_filter"));
    }

    private String registerAgent(String responseFilter) throws IOException {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("model_id", modelId);
        if (responseFilter != null) {
            parameters.put("response_filter", responseFilter);
        }
        Map<String, Object> agent = Map
            .of("name", "qpt-mock-agent", "type", "flow", "tools", List.of(Map.of("type", "QueryPlanningTool", "parameters", parameters)));
        Response response = TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, JSON.toJson(agent), null);
        return (String) parseResponseToMap(response).get("agent_id");
    }

    private Response execute(String agentId) throws IOException {
        String body = JSON.toJson(Map.of("parameters", Map.of("question", "Find shoes", "index_name", INDEX)));
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, body, null);
    }

    private String openaiConnector() {
        return "{\"name\":\"Mock OpenAI\",\"version\":1,\"protocol\":\"http\",\"parameters\":{\"model\":\"gpt\"},"
            + "\"credential\":{\"openAI_key\":\"k\"},"
            + "\"actions\":[{\"action_type\":\"predict\",\"method\":\"POST\",\"url\":\""
            + String.format(Locale.ROOT, "http://127.0.0.1:%d", mockLlm.getAddress().getPort())
            + "/v1/chat/completions\",\"headers\":{\"Authorization\":\"Bearer ${credential.openAI_key}\"},"
            + "\"request_body\":\"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": "
            + "[{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"${parameters.system_prompt}\\\"},"
            + "{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"${parameters.user_prompt}\\\"}] }\"}]}";
    }

    private void handleLlmRequest(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            in.readAllBytes();
        }
        Map<String, Object> message = Map.of("role", "assistant", "content", GENERATED_QUERY);
        Map<String, Object> response = Map
            .of(
                "id",
                "mock-1",
                "object",
                "chat.completion",
                "model",
                "gpt",
                "choices",
                List.of(Map.of("index", 0, "finish_reason", "stop", "message", message)),
                "usage",
                Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2)
            );
        byte[] bytes = JSON.toJson(response).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static boolean isContainerizedCluster() {
        return "docker-cluster".equals(System.getProperty("tests.clustername"));
    }
}
