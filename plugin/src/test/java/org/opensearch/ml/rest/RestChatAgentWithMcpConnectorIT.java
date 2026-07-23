/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

/**
 * Integration test for a conversational agent backed by an MCP streamable-http connector that
 * points at the very same cluster (which also hosts an MCP server at /_plugins/_ml/mcp).
 *
 * <p>This exercises the full MCP-client path end-to-end:
 * <ol>
 *   <li>Register ListIndexTool with the cluster's MCP server.</li>
 *   <li>Create an MCP streamable-http connector whose URL is the cluster's own HTTP endpoint.</li>
 *   <li>Register a conversational agent wired to Bedrock Claude plus the MCP connector.</li>
 *   <li>Execute the agent with an "list indices" question and assert that the pre-seeded
 *       iris_data index shows up in the final response — which can only happen if the MCP
 *       tool list succeeded and ListIndexTool was actually invoked.</li>
 * </ol>
 *
 * <p>Requires {@code AWS_ACCESS_KEY_ID} + {@code AWS_SECRET_ACCESS_KEY} (and optionally
 * {@code AWS_SESSION_TOKEN}); skipped automatically when absent.
 */
public class RestChatAgentWithMcpConnectorIT extends MLCommonsRestTestCase {

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String REGION = "us-west-2";
    private static final String MODEL_ID_BEDROCK = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

    // Random suffix so the LLM can't hallucinate the canonical "iris_data" and pass the assertion
    // without actually calling ListIndexTool through MCP.
    private final String irisIndex = "iris_data_mcp_it_" + randomAlphaOfLength(8).toLowerCase(Locale.ROOT);
    private String llmModelId;

    @Before
    public void setup() throws Exception {
        Assume.assumeNotNull(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);
        Assume
            .assumeFalse(
                "MCP loopback connector cannot authenticate on the containerized cluster leg",
                "docker-cluster".equals(System.getProperty("tests.clustername"))
            );

        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", true);
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));
        updateClusterSettings("plugins.ml_commons.mcp_connector_enabled", true);
        updateClusterSettings("plugins.ml_commons.mcp_server_enabled", true);

        // Register ListIndexTool with the cluster's MCP server so the MCP client can discover it.
        String toolRegistrationBody = "{\"tools\":[{\"name\":\"ListIndexTool\",\"type\":\"ListIndexTool\"}]}";
        Response registerResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp/tools/_register",
                null,
                toolRegistrationBody,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
        assertEquals(200, registerResponse.getStatusLine().getStatusCode());

        // Registration writes to the system index with an immediate refresh, and the MCP server
        // loads tools from that index per request, so the tool is servable as soon as register returns.
        String toolsListRequest = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\",\"params\":{}}";
        assertBusyWithFixedSleepTime(() -> {
            String toolsListBody;
            try {
                Response listResponse = TestHelper
                    .makeRequest(
                        client(),
                        "POST",
                        "/_plugins/_ml/mcp",
                        null,
                        toolsListRequest,
                        ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
                    );
                toolsListBody = TestHelper.httpEntityToString(listResponse.getEntity());
            } catch (Exception e) {
                // assertBusy only retries on AssertionError
                throw new AssertionError("MCP tools/list request failed: " + e.getMessage(), e);
            }
            assertTrue(
                "ListIndexTool did not become servable via MCP tools/list within 30s, got: " + toolsListBody,
                toolsListBody.contains("ListIndexTool")
            );
        }, TimeValue.timeValueSeconds(30), TimeValue.timeValueMillis(500));

        ingestIrisData(irisIndex);
        llmModelId = registerAndDeployBedrockModel();
    }

    @After
    public void teardown() throws IOException {
        if (AWS_ACCESS_KEY_ID == null
            || AWS_SECRET_ACCESS_KEY == null
            || "docker-cluster".equals(System.getProperty("tests.clustername"))) {
            return;
        }
        try {
            TestHelper
                .makeRequest(
                    client(),
                    "POST",
                    "/_plugins/_ml/mcp/tools/_remove",
                    null,
                    "[\"ListIndexTool\"]",
                    ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
                );
        } catch (Exception ignored) {
            // best-effort cleanup
        }
        deleteIndexWithAdminClient(irisIndex);
    }

    public void testChatAgentWithMcpStreamableHttpConnector() throws IOException {
        HttpHost host = getClusterHosts().get(0);
        String mcpServerUrl = host.getSchemeName() + "://" + host.getHostName() + ":" + host.getPort();

        // Step 1 – create the MCP streamable-http connector pointing at this cluster's own MCP
        // server. Generous client_config timeouts because the default 30s intermittently trips
        // on the loopback tool call under CI load.
        String connectorBody = "{\n"
            + "  \"name\": \"Self MCP Connector\",\n"
            + "  \"description\": \"MCP streamable-http connector pointing back at the same cluster's MCP server\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"mcp_streamable_http\",\n"
            + "  \"url\": \""
            + mcpServerUrl
            + "\",\n"
            + "  \"parameters\": {\n"
            + "    \"endpoint\": \"/_plugins/_ml/mcp\"\n"
            + "  },\n"
            + "  \"client_config\": {\n"
            + "    \"connection_timeout\": 120,\n"
            + "    \"read_timeout\": 120\n"
            + "  },\n"
            + "  \"credential\": {}\n"
            + "}";
        Response response = RestMLRemoteInferenceIT.createConnector(connectorBody);
        String mcpConnectorId = (String) parseResponseToMap(response).get("connector_id");
        assertNotNull(mcpConnectorId);

        // Step 2 – register a conversational agent wired to Bedrock + the MCP connector.
        String agentBody = "{\n"
            + "  \"name\": \"Test_Chat_Agent_With_MCP\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent that lists indices via an MCP tool\",\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + llmModelId
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"prompt\": \"${parameters.question}\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"parameters\": {\n"
            + "    \"_llm_interface\": \"bedrock/converse/claude\",\n"
            + "    \"mcp_connectors\": [\n"
            + "      {\n"
            + "        \"mcp_connector_id\": \""
            + mcpConnectorId
            + "\",\n"
            + "        \"tool_filters\": []\n"
            + "      }\n"
            + "    ]\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  }\n"
            + "}";
        response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(agentBody), null);
        String agentId = (String) parseResponseToMap(response).get("agent_id");
        assertNotNull(agentId);

        // Step 3 – execute. The LLM must pick ListIndexTool (sourced via MCP) to answer this.
        response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/" + agentId + "/_execute",
                null,
                TestHelper
                    .toHttpEntity("{\"parameters\":{\"question\":\"Use the ListIndexTool to list user indices and return their names.\"}}"),
                null
            );
        Map responseMap = parseResponseToMap(response);

        List inferenceResults = (List) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertFalse(inferenceResults.isEmpty());

        Map firstResult = (Map) inferenceResults.get(0);
        List output = (List) firstResult.get("output");
        assertNotNull(output);
        assertFalse(output.isEmpty());

        // The index name carries a random suffix seeded at test time, so the LLM cannot produce it
        // without the actual ListIndexTool output flowing back through MCP. Substring-matching this
        // exact name is therefore a sufficient proof that the MCP tool was invoked end-to-end.
        String finalResponse = output.toString();
        assertTrue(
            "Expected randomized index name '" + irisIndex + "' to appear in agent response, got: " + finalResponse,
            finalResponse.contains(irisIndex)
        );
    }

    private String registerAndDeployBedrockModel() throws IOException, InterruptedException {
        String sessionTokenLine = AWS_SESSION_TOKEN != null ? "    \"session_token\": \"" + AWS_SESSION_TOKEN + "\"\n" : "";
        String connectorBody = "{\n"
            + "  \"name\": \"Bedrock Claude Connector\",\n"
            + "  \"description\": \"Connector for AWS Bedrock Claude (converse API)\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"parameters\": {\n"
            + "    \"region\": \""
            + REGION
            + "\",\n"
            + "    \"service_name\": \"bedrock\",\n"
            + "    \"model\": \""
            + MODEL_ID_BEDROCK
            + "\"\n"
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"access_key\": \""
            + AWS_ACCESS_KEY_ID
            + "\",\n"
            + "    \"secret_key\": \""
            + AWS_SECRET_ACCESS_KEY
            + "\",\n"
            + sessionTokenLine
            + "  },\n"
            + "  \"actions\": [\n"
            + "    {\n"
            + "      \"action_type\": \"predict\",\n"
            + "      \"method\": \"POST\",\n"
            + "      \"url\": \"https://bedrock-runtime."
            + REGION
            + ".amazonaws.com/model/"
            + MODEL_ID_BEDROCK
            + "/converse\",\n"
            + "      \"headers\": {\n"
            + "        \"content-type\": \"application/json\",\n"
            + "        \"x-amz-content-sha256\": \"required\"\n"
            + "      },\n"
            + "      \"request_body\": \"{\\\"system\\\": [{\\\"text\\\": \\\"You are a helpful assistant.\\\"}], \\\"messages\\\": [${parameters._chat_history:-}{\\\"role\\\":\\\"user\\\",\\\"content\\\":[{\\\"text\\\":\\\"${parameters.prompt}\\\"}]}${parameters._interactions:-}]${parameters.tool_configs:-} }\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";

        Response response = RestMLRemoteInferenceIT.createConnector(connectorBody);
        String connectorId = (String) parseResponseToMap(response).get("connector_id");
        assertNotNull(connectorId);

        response = RestMLRemoteInferenceIT.registerRemoteModel("bedrock_claude_mcp_model", connectorId);
        String taskId = (String) parseResponseToMap(response).get("task_id");
        assertNotNull(taskId);
        waitForTask(taskId, MLTaskState.COMPLETED);

        response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
        String modelId = (String) parseResponseToMap(response).get("model_id");
        assertNotNull(modelId);

        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        taskId = (String) parseResponseToMap(response).get("task_id");
        assertNotNull(taskId);
        waitForTask(taskId, MLTaskState.COMPLETED);

        return modelId;
    }
}
