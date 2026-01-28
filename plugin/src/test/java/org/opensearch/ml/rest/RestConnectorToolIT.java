/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.containsString;
import static org.opensearch.ml.common.output.model.ModelTensor.DATA_AS_MAP_FIELD;
import static org.opensearch.ml.rest.RestMLRemoteInferenceIT.disableClusterConnectorAccessControl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.hc.core5.http.ParseException;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.utils.TestHelper;

public class RestConnectorToolIT extends RestBaseAgentToolsIT {
    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");

    private static final String GITHUB_CI_AWS_REGION = "us-west-2";
    private static final String BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET = "anthropic.claude-3-5-sonnet-20240620-v1:0";

    private String bedrockClaudeConnectorId;
    private String bedrockClaudeConnectorIdForPredict;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        disableClusterConnectorAccessControl();
        Thread.sleep(20000);
        this.bedrockClaudeConnectorId = createBedrockClaudeConnector("execute");
        this.bedrockClaudeConnectorIdForPredict = createBedrockClaudeConnector("predict");
    }

    private String createBedrockClaudeConnector(String action) throws IOException, InterruptedException {
        String bedrockClaudeConnectorEntity = "{\n"
            + "  \"name\": \"Bedrock Connector: claude 3.5\",\n"
            + "  \"description\": \"The connector to bedrock claude 3.5 model\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"parameters\": {\n"
            + "    \"region\": \""
            + GITHUB_CI_AWS_REGION
            + "\",\n"
            + "    \"service_name\": \"bedrock\",\n"
            + "    \"model\": \""
            + BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET
            + "\",\n"
            + "    \"system_prompt\": \"You are a helpful assistant.\",\n"
            + "\"response_filter\": \"$.output.message.content[0].text\""
            + "  },\n"
            + "  \"credential\": {\n"
            + "    \"access_key\": \""
            + AWS_ACCESS_KEY_ID
            + "\",\n"
            + "    \"secret_key\": \""
            + AWS_SECRET_ACCESS_KEY
            + "\",\n"
            + "    \"session_token\": \""
            + AWS_SESSION_TOKEN
            + "\"\n"
            + "  },\n"
            + "  \"actions\": [\n"
            + "        {\n"
            + "            \"action_type\": \""
            + action
            + "\",\n"
            + "            \"method\": \"POST\",\n"
            + "            \"headers\": {\n"
            + "                \"content-type\": \"application/json\"\n"
            + "            },\n"
            + "            \"url\": \"https://bedrock-runtime."
            + GITHUB_CI_AWS_REGION
            + ".amazonaws.com/model/"
            + BEDROCK_ANTHROPIC_CLAUDE_3_5_SONNET
            + "/converse\",\n"
            + "            \"request_body\": \"{ \\\"system\\\": [{\\\"text\\\": \\\"you are a helpful assistant.\\\"}], \\\"messages\\\":[{\\\"role\\\": \\\"user\\\", \\\"content\\\":[ {\\\"type\\\": \\\"text\\\", \\\"text\\\":\\\"${parameters.messages}\\\"}]}] , \\\"inferenceConfig\\\": {\\\"temperature\\\": 0.0, \\\"topP\\\": 0.9, \\\"maxTokens\\\": 1000} }\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";
        return registerConnector(bedrockClaudeConnectorEntity);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testConnectorToolInFlowAgent_WrongAction() throws IOException, ParseException {
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String registerAgentRequestBody = "{\n"
            + "  \"name\": \"Test agent with connector tool\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"This is a demo agent for connector tool\",\n"
            + "  \"app_type\": \"test1\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"ConnectorTool\",\n"
            + "      \"name\": \"bedrock_model\",\n"
            + "      \"parameters\": {\n"
            + "        \"connector_id\": \""
            + bedrockClaudeConnectorIdForPredict
            + "\",\n"
            + "        \"connector_action\": \"EXECUTE\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n" + "  \"parameters\": {\n" + "    \"question\": \"hello\"\n" + "  }\n" + "}";
        Exception exception = assertThrows(ResponseException.class, () -> executeAgent(agentId, agentInput));
        MatcherAssert.assertThat(exception.getMessage(), containsString("no EXECUTE action found"));
    }

    public void testConnectorToolInFlowAgent() throws IOException, ParseException {
        if (AWS_ACCESS_KEY_ID == null || AWS_SECRET_ACCESS_KEY == null || AWS_SESSION_TOKEN == null) {
            return;
        }
        String registerAgentRequestBody = "{\n"
            + "  \"name\": \"Test agent with connector tool\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"This is a demo agent for connector tool\",\n"
            + "  \"app_type\": \"test1\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"ConnectorTool\",\n"
            + "      \"name\": \"bedrock_model\",\n"
            + "      \"parameters\": {\n"
            + "        \"connector_id\": \""
            + bedrockClaudeConnectorId
            + "\",\n"
            + "        \"connector_action\": \"execute\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n" + "  \"parameters\": {\n" + "    \"messages\": \"hello\"\n" + "  }\n" + "}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/" + agentId + "/_execute", null, agentInput, null);
        String result = parseResponseFromResponse(response);
        assertEquals(RestStatus.OK, RestStatus.fromCode(response.getStatusLine().getStatusCode()));
        assertNotNull(result);
    }

    private String parseResponseFromResponse(Response response) throws IOException, ParseException {
        Map<String, Object> responseInMap = parseResponseToMap(response);
        return Optional
            .ofNullable(responseInMap)
            .map(m -> (List<Object>) m.get(ModelTensorOutput.INFERENCE_RESULT_FIELD))
            .filter(l -> !l.isEmpty())
            .map(l -> (Map<String, Object>) l.get(0))
            .map(m -> (List<Object>) m.get(ModelTensors.OUTPUT_FIELD))
            .filter(l -> !l.isEmpty())
            .map(l -> (Map<String, Object>) l.get(0))
            .map(m -> (Map<String, Object>) m.get(DATA_AS_MAP_FIELD))
            .map(m -> (String) m.get("response"))
            .orElseThrow(() -> new AssertionError("Unable to parse response from agent execution"));
    }
}
