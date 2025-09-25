/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_SEARCH_ENABLED;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.GENERATION_TYPE_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.MODEL_ID_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.SEARCH_TEMPLATES_FIELD;
import static org.opensearch.ml.engine.tools.QueryPlanningTool.USER_SEARCH_TEMPLATES_TYPE_FIELD;

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
    private static final String OPENAI_KEY = System.getenv("OPENAI_KEY");
    private final String openaiConnectorEntity = "{\n"
        + "    \"name\": \"My openai connector: gpt-4o\",\n"
        + "    \"description\": \"The connector to openai chat model\",\n"
        + "    \"version\": 1,\n"
        + "    \"protocol\": \"http\",\n"
        + "    \"parameters\": {\n"
        + "      \"model\": \"gpt-4o\"\n"
        + "    },\n"
        + "    \"credential\": {\n"
        + "      \"openAI_key\": \""
        + OPENAI_KEY
        + "\"\n"
        + "    },\n"
        + "    \"actions\": [\n"
        + "      {\n"
        + "        \"action_type\": \"predict\",\n"
        + "        \"method\": \"POST\",\n"
        + "        \"url\": \"https://api.openai.com/v1/chat/completions\",\n"
        + "        \"headers\": {\n"
        + "          \"Authorization\": \"Bearer ${credential.openAI_key}\"\n"
        + "        },\n"
        + "        \"request_body\": \"{ \\\"model\\\": \\\"${parameters.model}\\\", \\\"messages\\\": [{\\\"role\\\":\\\"system\\\",\\\"content\\\":\\\"${parameters.system_prompt}\\\"},{\\\"role\\\":\\\"user\\\",\\\"content\\\":\\\"${parameters.user_prompt}\\\"}]}\"\n"
        + "      }\n"
        + "    ]\n"
        + "}";

    @Before
    public void setup() throws IOException, InterruptedException {
        ingestIrisIndexData();
        if (OPENAI_KEY == null) {
            return;
        }
        // enable agentic search
        updateClusterSettings(ML_COMMONS_AGENTIC_SEARCH_ENABLED.getKey(), true);
        queryPlanningModelId = registerQueryPlanningModel();
    }

    @After
    public void deleteIndices() throws IOException {
        deleteIndex(IRIS_INDEX);
    }

    @Test
    public void testAgentWithQueryPlanningTool_DefaultPrompt() throws IOException {
        if (OPENAI_KEY == null) {
            return;
        }
        String agentName = "Test_QueryPlanningAgent_DefaultPrompt";
        String agentId = registerAgentWithQueryPlanningTool(agentName, queryPlanningModelId);
        assertNotNull(agentId);

        String query = "{\"parameters\": {\"query_text\": \"List 5 iris flowers of type setosa\"}}";
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

    @Test
    public void testAgentWithQueryPlanningTool_SearchTemplates() throws IOException {
        if (OPENAI_KEY == null) {
            return;
        }

        // Create Search Templates
        String templateBody = "{\"script\":{\"lang\":\"mustache\",\"source\":{\"query\":{\"match\":{\"type\":\"{{type}}\"}}}}}";
        Response response = createSearchTemplate("type_search_template", templateBody);
        templateBody = "{\"script\":{\"lang\":\"mustache\",\"source\":{\"query\":{\"term\":{\"type\":\"{{type}}\"}}}}}";
        response = createSearchTemplate("type_search_template_2", templateBody);

        // Register agent with search template IDs
        String agentName = "Test_AgentWithQueryPlanningTool_SearchTemplates";
        String searchTemplates = "[{"
            + "\"template_id\":\"type_search_template\","
            + "\"template_description\":\"this templates searches for flowers that match the given type this uses a match query\""
            + "},{"
            + "\"template_id\":\"type_search_template_2\","
            + "\"template_description\":\"this templates searches for flowers that match the given type this uses a term query\""
            + "},{"
            + "\"template_id\":\"brand_search_template\","
            + "\"template_description\":\"this templates searches for products that match the given brand\""
            + "}]";
        String agentId = registerQueryPlanningAgentWithSearchTemplates(agentName, queryPlanningModelId, searchTemplates);
        assertNotNull(agentId);

        String query = "{\"parameters\": {\"query_text\": \"List 5 iris flowers of type setosa\"}}";
        Response agentResponse = executeAgent(agentId, query);
        String responseBody = TestHelper.httpEntityToString(agentResponse.getEntity());

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

    private String registerQueryPlanningAgentWithSearchTemplates(String agentName, String modelId, String searchTemplates)
        throws IOException {
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
            .parameters(
                Map
                    .ofEntries(
                        Map.entry(MODEL_ID_FIELD, modelId),
                        Map.entry(GENERATION_TYPE_FIELD, USER_SEARCH_TEMPLATES_TYPE_FIELD),
                        Map.entry(SEARCH_TEMPLATES_FIELD, searchTemplates)
                    )
            )
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
        String openaiModelName = "openai gpt-4o model " + randomAlphaOfLength(5);
        return registerRemoteModel(openaiConnectorEntity, openaiModelName, true);
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

    private Response createSearchTemplate(String templateName, String templateBody) throws IOException {
        return TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_scripts/" + templateName,
                null,
                new StringEntity(templateBody),
                List.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    private void deleteAgent(String agentId) throws IOException {
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", List.of());
    }
}
