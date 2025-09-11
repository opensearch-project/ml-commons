/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.containsString;
import static org.opensearch.ml.rest.RestMLRemoteInferenceIT.disableClusterConnectorAccessControl;

import java.io.IOException;

import org.apache.hc.core5.http.ParseException;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.ResponseException;

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
            + "        \"connector_action\": \"predict\"\n"
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
        String result = executeAgent(agentId, agentInput);
        assertNotNull(result);
    }

}
