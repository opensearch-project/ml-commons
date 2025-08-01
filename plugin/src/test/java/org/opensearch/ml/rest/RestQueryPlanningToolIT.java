/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.utils.TestHelper;

public class RestQueryPlanningToolIT extends MLCommonsRestTestCase {

    private static final String IRIS_INDEX = "iris_data";
    private String queryPlanningModelId;
    private static final String AWS_ACCESS_KEY_ID = System.getenv("AWS_ACCESS_KEY_ID");
    private static final String AWS_SECRET_ACCESS_KEY = System.getenv("AWS_SECRET_ACCESS_KEY");
    private static final String AWS_SESSION_TOKEN = System.getenv("AWS_SESSION_TOKEN");
    private static final String GITHUB_CI_AWS_REGION = "us-west-2";
    private final String bedrockClaudeModelConnectorEntity = "{\n"
        + "    \"name\": \"Amazon Bedrock Claude 3.7-sonnet connector\",\n"
        + "    \"description\": \"connector for base agent with tools\",\n"
        + "    \"version\": 1,\n"
        + "    \"protocol\": \"aws_sigv4\",\n"
        + "    \"parameters\": {\n"
        + "      \"region\": \""
        + GITHUB_CI_AWS_REGION
        + "\",\n"
        + "      \"service_name\": \"bedrock\",\n"
        + "      \"model\": \"us.anthropic.claude-3-7-sonnet-20250219-v1:0\",\n"
        + "      \"system_prompt\":\"please help answer the user question. \"\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "      \"access_key\":\" "
        + AWS_ACCESS_KEY_ID
        + "\",\n"
        + "      \"secret_key\": \""
        + AWS_SECRET_ACCESS_KEY
        + "\",\n"
        + "      \"session_token\": \""
        + AWS_SESSION_TOKEN
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "      {\n"
        + "        \"action_type\": \"predict\",\n"
        + "        \"method\": \"POST\",\n"
        + "        \"url\": \"https://bedrock-runtime.${parameters.region}.amazonaws.com/model/${parameters.model}/converse\",\n"
        + "        \"headers\": {\n"
        + "          \"content-type\": \"application/json\"\n"
        + "        },\n"
        + "        \"request_body\": \"{ \\\"system\\\": [{\\\"text\\\": \\\"${parameters.system_prompt}\\\"}], \\\"messages\\\": [{\\\"role\\\":\\\"user\\\",\\\"content\\\":[{\\\"text\\\":\\\"${parameters.prompt}\\\"}]}]}\"\n"
        + "      }\n"
        + "    ]\n"
        + "}";

    @Before
    public void setup() throws IOException, InterruptedException {
        ingestIrisIndexData();
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        queryPlanningModelId = registerQueryPlanningModel();
    }

    @After
    public void deleteIndices() throws IOException {
        deleteIndex(IRIS_INDEX);
    }

    @Test
    public void testAgentWithQueryPlanningTool_DefaultPrompt() throws IOException {
        if (AWS_ACCESS_KEY_ID == null) {
            return;
        }
        String agentName = "Test_QueryPlanningAgent_DefaultPrompt";
        String agentId = registerAgentWithQueryPlanningTool(agentName, queryPlanningModelId);
        assertNotNull(agentId);

        String query = "{\"parameters\": {\"query_text\": \"How many iris flowers of type setosa are there?\"}}";
        Response response = executeAgent(agentId, query);
        String responseBody = TestHelper.httpEntityToString(response.getEntity());

        Map<String, Object> responseMap = gson.fromJson(responseBody, Map.class);

        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) responseMap.get("inference_results");
        Map<String, Object> firstResult = inferenceResults.get(0);
        List<Map<String, Object>> outputArray = (List<Map<String, Object>>) firstResult.get("output");
        Map<String, Object> output = (Map<String, Object>) outputArray.get(0);
        String result = output.get("result").toString();

        assertTrue(result.contains("query"));
        deleteAgent(agentId);
    }

    private String registerAgentWithQueryPlanningTool(String agentName, String modelId) throws IOException {
        MLToolSpec listIndexTool = MLToolSpec
            .builder()
            .type("ListIndexTool")
            .name("MyListIndexTool")
            .description("A tool for list indices")
            .parameters(Map.of("index", IRIS_INDEX, "question", "what fields are in the index?"))
            .includeOutputInAgentResponse(true)
            .build();

        MLToolSpec queryPlanningTool = MLToolSpec
            .builder()
            .type("QueryPlanningTool")
            .name("MyQueryPlanningTool")
            .description("A tool for planning queries")
            .parameters(Map.of(MODEL_ID_FIELD, modelId))
            .includeOutputInAgentResponse(true)
            .build();

        MLAgent agent = MLAgent
            .builder()
            .name(agentName)
            .type("flow")
            .description("Test agent with QueryPlanningTool")
            .tools(List.of(listIndexTool, queryPlanningTool))
            .build();

        return registerAgent(agentName, agent);
    }

    private String registerQueryPlanningModel() throws IOException, InterruptedException {
        String bedrockClaudeModelName = "bedrock claude model " + randomAlphaOfLength(5);
        return registerRemoteModel(bedrockClaudeModelConnectorEntity, bedrockClaudeModelName, true);
    }

    private void ingestIrisIndexData() throws IOException {
        String bulkRequestBody = "{\"index\":{\"_index\":\""
            + IRIS_INDEX
            + "\",\"_id\":\"1\"}}\n"
            + "{\"petal_length_in_cm\":1.4,\"petal_width_in_cm\":0.2,\"sepal_length_in_cm\":5.1,\"sepal_width_in_cm\":3.5,\"species\":\"setosa\"}\n"
            + "{\"index\":{\"_index\":\""
            + IRIS_INDEX
            + "\",\"_id\":\"2\"}}\n"
            + "{\"petal_length_in_cm\":4.5,\"petal_width_in_cm\":1.5,\"sepal_length_in_cm\":6.4,\"sepal_width_in_cm\":2.9,\"species\":\"versicolor\"}\n";
        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_bulk",
                null,
                new StringEntity(bulkRequestBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
        TestHelper.makeRequest(client(), "POST", "/" + IRIS_INDEX + "/_refresh", null, "", List.of());
    }

    private String registerAgent(String agentName, MLAgent agent) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/_register",
                null,
                new StringEntity(gson.toJson(agent)),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
        Map<String, String> responseMap = gson.fromJson(TestHelper.httpEntityToString(response.getEntity()), Map.class);
        return responseMap.get("agent_id");
    }

    private Response executeAgent(String agentId, String query) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/" + agentId + "/_execute",
                null,
                new StringEntity(query),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private void deleteAgent(String agentId) throws IOException {
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", List.of());
    }
}
