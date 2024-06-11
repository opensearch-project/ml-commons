/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import java.text.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

public class RestMLFlowAgentIT extends MLCommonsRestTestCase {

    private String irisIndex = "iris_data";

    @Before
    public void setup() throws IOException, ParseException {
        ingestIrisData(irisIndex);
    }

    @After
    public void deleteIndices() throws IOException {
        deleteIndexWithAdminClient(irisIndex);
    }

    public void testAgentCatIndexTool() throws IOException {
        // Register agent with CatIndexTool.
        Response response = registerAgentWithCatIndexTool();
        Map responseMap = parseResponseToMap(response);
        String agentId = (String) responseMap.get("agent_id");
        assertNotNull(agentId);
        assertEquals(20, agentId.length());

        // Execute agent.
        response = executeAgentCatIndexTool(agentId);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        assertEquals("response", responseMap.get("name"));
        String result = (String) responseMap.get("result");
        assertNotNull(result);
        assertTrue(result.contains(".plugins-ml-agent"));
    }

    public void testAgentSearchIndexTool() throws IOException {
        // Register agent with SearchIndexTool.
        Response response = registerAgentWithSearchIndexTool();
        Map responseMap = parseResponseToMap(response);
        String agentId = (String) responseMap.get("agent_id");
        assertNotNull(agentId);
        assertEquals(20, agentId.length());

        // Execute agent.
        response = executeAgentSearchIndexTool(agentId);
        responseMap = parseResponseToMap(response);
        List responseList = (List) responseMap.get("inference_results");
        responseMap = (Map) responseList.get(0);
        responseList = (List) responseMap.get("output");
        responseMap = (Map) responseList.get(0);
        assertEquals("response", responseMap.get("name"));
        String result = (String) responseMap.get("result");
        assertNotNull(result);
        assertTrue(result.contains("\"_source\":{\"petal_length_in_cm\""));
    }

    public static Response registerAgentWithCatIndexTool() throws IOException {
        String registerAgentEntity = "{\n"
            + "  \"name\": \"Test_Agent_For_CatIndex_tool\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"this is a test agent for the CatIndexTool\",\n"
            + "  \"tools\": [\n"
            + "      {\n"
            + "      \"type\": \"CatIndexTool\",\n"
            + "      \"name\": \"DemoCatIndexTool\",\n"
            + "      \"parameters\": {\n"
            + "        \"input\": \"${parameters.question}\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentEntity), null);
    }

    public static Response registerAgentWithSearchIndexTool() throws IOException {
        String registerAgentEntity = "{\n"
            + "  \"name\": \"Test_Agent_For_SearchIndex_tool\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"this is a test agent for the SearchIndexTool\",\n"
            + "  \"tools\": [\n"
            + "      {\n"
            + "      \"type\": \"SearchIndexTool\""
            + "      }\n"
            + "  ]\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerAgentEntity), null);
    }

    public static Response executeAgentCatIndexTool(String agentId) throws IOException {
        String question = "\"How many indices do I have?\"";
        return executeAgent(agentId, Map.of("question", question));
    }

    public static Response executeAgentSearchIndexTool(String agentId) throws IOException {
        String input = "{\"index\": \"iris_data\", \"query\": {\"size\": 2,  \"_source\": \"petal_length_in_cm\"}}";
        return executeAgent(agentId, Map.of("input", input));
    }

    public static Response executeAgent(String agentId, Map<String, String> args) throws IOException {
        if (args == null || args.isEmpty()) {
            return null;
        }

        // Construct parameters.
        StringBuilder entityBuilder = new StringBuilder("{\"parameters\":{");
        for (Map.Entry entry : args.entrySet()) {
            entityBuilder.append('"').append(entry.getKey()).append("\":").append(entry.getValue()).append(',');
        }
        entityBuilder.replace(entityBuilder.length() - 1, entityBuilder.length(), "}}");

        return TestHelper
            .makeRequest(
                client(),
                "POST",
                String.format("/_plugins/_ml/agents/%s/_execute", agentId),
                null,
                TestHelper.toHttpEntity(entityBuilder.toString()),
                null
            );
    }
}
