/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for a conversational (chat) agent that delegates to a flow agent via AgentTool.
 *
 * <p>Setup:
 * <ol>
 *   <li>Inner agent – a flow agent with ListIndexTool that queries iris_data.</li>
 *   <li>Outer agent – a conversational agent backed by AWS Bedrock Claude with a single AgentTool
 *       whose {@code agent_id} points to the inner agent.</li>
 * </ol>
 *
 * <p>The test exercises the full AgentTool execution path end-to-end: the conversational runner
 * calls the LLM, the LLM decides to invoke AgentTool, AgentTool executes the inner flow agent,
 * and the final answer is returned to the caller.
 *
 * <p>Requires the following environment variables:
 * <ul>
 *   <li>{@code AWS_ACCESS_KEY_ID}</li>
 *   <li>{@code AWS_SECRET_ACCESS_KEY}</li>
 *   <li>{@code AWS_SESSION_TOKEN} (optional)</li>
 * </ul>
 * The test is skipped automatically when these are absent.
 */
public class RestChatAgentIT extends MLCommonsRestTestCase {

    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String REGION = "us-west-2";
    private static final String MODEL_ID_BEDROCK = "us.anthropic.claude-haiku-4-5-20251001-v1:0";

    private String irisIndex = "iris_data";
    private String llmModelId;

    @Before
    public void setup() throws IOException, ParseException, InterruptedException {
        Assume.assumeNotNull(AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY);

        RestMLRemoteInferenceIT.disableClusterConnectorAccessControl();
        updateClusterSettings("plugins.ml_commons.memory_feature_enabled", true);
        updateClusterSettings("plugins.ml_commons.only_run_on_ml_node", false);
        updateClusterSettings("plugins.ml_commons.trusted_connector_endpoints_regex", List.of("^.*$"));

        ingestIrisData(irisIndex);
        llmModelId = registerAndDeployBedrockModel();
    }

    @After
    public void deleteIndices() throws IOException {
        if (AWS_ACCESS_KEY_ID != null && AWS_SECRET_ACCESS_KEY != null) {
            deleteIndexWithAdminClient(irisIndex);
        }
    }

    public void testChatAgentWithAgentTool() throws IOException {
        // Step 1 – register the inner flow agent backed by ListIndexTool.
        String innerAgentBody = "{\n"
            + "  \"name\": \"Test_Inner_Flow_Agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"Inner flow agent that lists indices\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"ListIndexTool\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(innerAgentBody), null);
        Map responseMap = parseResponseToMap(response);
        String innerAgentId = (String) responseMap.get("agent_id");
        assertNotNull(innerAgentId);

        // Step 2 – register the outer conversational agent with AgentTool pointing at the inner agent.
        String outerAgentBody = "{\n"
            + "  \"name\": \"Test_Chat_Agent\",\n"
            + "  \"type\": \"conversational\",\n"
            + "  \"description\": \"Conversational agent that delegates to the inner flow agent via AgentTool\",\n"
            + "  \"llm\": {\n"
            + "    \"model_id\": \""
            + llmModelId
            + "\",\n"
            + "    \"parameters\": {\n"
            + "      \"prompt\": \"${parameters.question}\"\n"
            + "    }\n"
            + "  },\n"
            + "  \"parameters\": {\n"
            + "    \"_llm_interface\": \"bedrock/converse/claude\"\n"
            + "  },\n"
            + "  \"memory\": {\n"
            + "    \"type\": \"conversation_index\"\n"
            + "  },\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"AgentTool\",\n"
            + "      \"name\": \"AgentTool\",\n"
            + "      \"description\": \"A tool that executes a sub-agent to list OpenSearch indices\",\n"
            + "      \"parameters\": {\n"
            + "        \"agent_id\": \""
            + innerAgentId
            + "\"\n"
            + "      },\n"
            + "      \"attributes\": {\n"
            + "        \"input_schema\": \"{\\\"type\\\":\\\"object\\\",\\\"properties\\\":{}}\",\n"
            + "        \"strict\": \"false\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(outerAgentBody), null);
        responseMap = parseResponseToMap(response);
        String outerAgentId = (String) responseMap.get("agent_id");
        assertNotNull(outerAgentId);

        // Step 3 – execute the outer agent.
        response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/" + outerAgentId + "/_execute",
                null,
                TestHelper.toHttpEntity("{\"parameters\":{\"question\":\"List indices\"}}"),
                null
            );
        responseMap = parseResponseToMap(response);

        List inferenceResults = (List) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertFalse(inferenceResults.isEmpty());

        Map firstResult = (Map) inferenceResults.get(0);
        List output = (List) firstResult.get("output");
        assertNotNull(output);
        assertFalse(output.isEmpty());
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
        Map responseMap = parseResponseToMap(response);
        String connectorId = (String) responseMap.get("connector_id");
        assertNotNull(connectorId);

        response = RestMLRemoteInferenceIT.registerRemoteModel("bedrock_claude_model", connectorId);
        responseMap = parseResponseToMap(response);
        String taskId = (String) responseMap.get("task_id");
        assertNotNull(taskId);
        waitForTask(taskId, MLTaskState.COMPLETED);

        response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/tasks/" + taskId, null, "", null);
        responseMap = parseResponseToMap(response);
        String modelId = (String) responseMap.get("model_id");
        assertNotNull(modelId);

        response = RestMLRemoteInferenceIT.deployRemoteModel(modelId);
        responseMap = parseResponseToMap(response);
        taskId = (String) responseMap.get("task_id");
        assertNotNull(taskId);
        waitForTask(taskId, MLTaskState.COMPLETED);

        return modelId;
    }
}
