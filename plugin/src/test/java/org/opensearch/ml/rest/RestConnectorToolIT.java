/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.hamcrest.Matchers.containsString;

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

    private String bedrockClaudeConnectorId;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        String bedrockClaudeConnectorEntity = "{\n"
            + "  \"name\": \"BedRock Claude instant-v1 Connector \",\n"
            + "  \"description\": \"The connector to BedRock service for claude model\",\n"
            + "  \"version\": 1,\n"
            + "  \"protocol\": \"aws_sigv4\",\n"
            + "  \"parameters\": {\n"
            + "    \"region\": \""
            + GITHUB_CI_AWS_REGION
            + "\",\n"
            + "    \"service_name\": \"bedrock\",\n"
            + "    \"anthropic_version\": \"bedrock-2023-05-31\",\n"
            + "    \"max_tokens_to_sample\": 8000,\n"
            + "    \"temperature\": 0.0001,\n"
            + "    \"response_filter\": \"$.completion\"\n"
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
            + "    {\n"
            + "      \"action_type\": \"execute\",\n"
            + "      \"method\": \"POST\",\n"
            + "      \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/anthropic.claude-instant-v1/invoke\",\n"
            + "      \"headers\": {\n"
            + "        \"content-type\": \"application/json\",\n"
            + "        \"x-amz-content-sha256\": \"required\"\n"
            + "      },\n"
            + "      \"request_body\": \"{\\\"prompt\\\":\\\"\\\\n\\\\nHuman:${parameters.question}\\\\n\\\\nAssistant:\\\", \\\"max_tokens_to_sample\\\":${parameters.max_tokens_to_sample}, \\\"temperature\\\":${parameters.temperature},  \\\"anthropic_version\\\":\\\"${parameters.anthropic_version}\\\" }\"\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        this.bedrockClaudeConnectorId = registerConnector(bedrockClaudeConnectorEntity);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testConnectorToolInFlowAgent_WrongAction() throws IOException, ParseException {
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
            + "        \"connector_action\": \"predict\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        String agentId = createAgent(registerAgentRequestBody);
        String agentInput = "{\n" + "  \"parameters\": {\n" + "    \"question\": \"hello\"\n" + "  }\n" + "}";
        Exception exception = assertThrows(ResponseException.class, () -> executeAgent(agentId, agentInput));
        MatcherAssert.assertThat(exception.getMessage(), containsString("no execute action found"));
    }

    public void testConnectorToolInFlowAgent() throws IOException, ParseException {
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
        String agentInput = "{\n" + "  \"parameters\": {\n" + "    \"question\": \"hello\"\n" + "  }\n" + "}";
        String result = executeAgent(agentId, agentInput);
        assertNotNull(result);
    }

}
