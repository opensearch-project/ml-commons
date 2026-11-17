/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.settings.MLCommonsSettings;
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
        // Reset the depth setting so cross-test ordering doesn't leak state.
        resetMaxAgentCallDepth();
    }

    public void testAgentListIndexTool() throws IOException {
        // Register agent with ListIndexTool.
        Response response = registerAgentWithListIndexTool();
        Map responseMap = parseResponseToMap(response);
        String agentId = (String) responseMap.get("agent_id");
        assertNotNull(agentId);
        assertEquals(20, agentId.length());

        // Execute agent.
        response = executeAgentListIndexTool(agentId);
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

    public void testMaxAgentCallDepthSettingIsConfigurable() throws IOException, ParseException {
        // Bump the max depth to 3 — within range. Setting must accept and surface in subsequent errors.
        Response setResp = setMaxAgentCallDepth(3);
        assertEquals(200, setResp.getStatusLine().getStatusCode());

        Response registerResponse = registerSelfReferencingAgentSkeleton();
        Map registerMap = parseResponseToMap(registerResponse);
        String agentId = (String) registerMap.get("agent_id");
        assertNotNull(agentId);
        Response updateResponse = updateAgentToolToTargetSelf(agentId);
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        ResponseException ex = expectThrows(ResponseException.class, () -> executeAgent(agentId, Map.of("question", "\"hi\"")));
        assertEquals(400, ex.getResponse().getStatusLine().getStatusCode());
        String body = EntityUtils.toString(ex.getResponse().getEntity());
        assertTrue(
            "Expected bound to reflect configured max=3, got: " + body,
            body.contains("AgentTool nested call depth exceeded the maximum (3)")
        );
    }

    public void testMaxAgentCallDepthSettingRejectsOutOfRangeValues() throws IOException {
        // intSetting(min=1, max=5) — values outside this range must be rejected by the cluster.
        ResponseException tooSmall = expectThrows(ResponseException.class, () -> setMaxAgentCallDepth(0));
        assertEquals(400, tooSmall.getResponse().getStatusLine().getStatusCode());

        ResponseException tooLarge = expectThrows(ResponseException.class, () -> setMaxAgentCallDepth(99));
        assertEquals(400, tooLarge.getResponse().getStatusLine().getStatusCode());
    }

    public void testAgentToolSelfReferenceIsBoundedByMaxRecursionDepth() throws IOException, ParseException {
        // Register a flow agent whose only tool is an AgentTool pointing at a placeholder id.
        Response registerResponse = registerSelfReferencingAgentSkeleton();
        Map registerMap = parseResponseToMap(registerResponse);
        String agentId = (String) registerMap.get("agent_id");
        assertNotNull(agentId);

        // Rewire the AgentTool to point at the agent itself, so executing it tries to recurse.
        Response updateResponse = updateAgentToolToTargetSelf(agentId);
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        // Executing must fail fast with our recursion-depth guard rather than recursing forever.
        ResponseException ex = expectThrows(ResponseException.class, () -> executeAgent(agentId, Map.of("question", "\"hi\"")));
        // Guarantee the failure took the depth-bound path, not some upstream 404/500.
        assertEquals(400, ex.getResponse().getStatusLine().getStatusCode());
        String body = EntityUtils.toString(ex.getResponse().getEntity());
        assertTrue("Expected nested-call-depth error, got: " + body, body.contains("AgentTool nested call depth exceeded"));
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

    public static Response registerAgentWithListIndexTool() throws IOException {
        String registerAgentEntity = "{\n"
            + "  \"name\": \"Test_Agent_For_CatIndex_tool\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"this is a test agent for the ListIndexTool\",\n"
            + "  \"tools\": [\n"
            + "      {\n"
            + "      \"type\": \"ListIndexTool\",\n"
            + "      \"name\": \"DemoListIndexTool\",\n"
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

    public static Response executeAgentListIndexTool(String agentId) throws IOException {
        String question = "\"How many indices do I have?\"";
        return executeAgent(agentId, Map.of("question", question));
    }

    public static Response executeAgentSearchIndexTool(String agentId) throws IOException {
        String input = "{\"index\": \"iris_data\", \"query\": {\"size\": 2,  \"_source\": \"petal_length_in_cm\"}}";
        return executeAgent(agentId, Map.of("input", input));
    }

    public static Response registerSelfReferencingAgentSkeleton() throws IOException {
        // agent_id is a placeholder; we PUT-update it to the agent's own id after registration.
        String registerEntity = "{\n"
            + "  \"name\": \"Test_Agent_Self_Reference\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"agent that calls itself via AgentTool\",\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"AgentTool\",\n"
            + "      \"name\": \"NestedAgentTool\",\n"
            + "      \"parameters\": {\n"
            + "        \"agent_id\": \"placeholder-will-be-replaced\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerEntity), null);
    }

    public static Response updateAgentToolToTargetSelf(String agentId) throws IOException {
        String updateEntity = "{\n"
            + "  \"tools\": [\n"
            + "    {\n"
            + "      \"type\": \"AgentTool\",\n"
            + "      \"name\": \"NestedAgentTool\",\n"
            + "      \"parameters\": {\n"
            + "        \"agent_id\": \""
            + agentId
            + "\"\n"
            + "      }\n"
            + "    }\n"
            + "  ]\n"
            + "}";
        return TestHelper
            .makeRequest(client(), "PUT", "/_plugins/_ml/agents/" + agentId, null, TestHelper.toHttpEntity(updateEntity), null);
    }

    public static Response setMaxAgentCallDepth(int value) throws IOException {
        String body = "{\"persistent\":{\"" + MLCommonsSettings.ML_COMMONS_AGENT_MAX_CALL_DEPTH.getKey() + "\":" + value + "}}";
        return TestHelper.makeRequest(client(), "PUT", "_cluster/settings", null, TestHelper.toHttpEntity(body), null);
    }

    public static Response resetMaxAgentCallDepth() throws IOException {
        // null clears the persistent override, returning the setting to its default (1).
        String body = "{\"persistent\":{\"" + MLCommonsSettings.ML_COMMONS_AGENT_MAX_CALL_DEPTH.getKey() + "\":null}}";
        return TestHelper.makeRequest(client(), "PUT", "_cluster/settings", null, TestHelper.toHttpEntity(body), null);
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
