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
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

/**
 * REST-level coverage for tool search's configuration surface: the cluster settings and the
 * per-agent {@code tool_selection} block. Model-free, so search itself is covered only by the unit
 * tests in org.opensearch.ml.engine.tools.selection.
 */
public class RestMLDynamicToolSelectionIT extends RestBaseAgentToolsIT {

    private static final String ENABLED_KEY = "plugins.ml_commons.tool_selection.enabled";
    private static final String MODEL_ID_KEY = "plugins.ml_commons.tool_selection.embedding_model_id";
    private static final String SEARCH_TIMEOUT_KEY = "plugins.ml_commons.tool_selection.search_timeout";

    private static final String AGENT_WITH_TOOL_SELECTION = "{\n"
        + "  \"name\": \"tool-selection-it-agent\",\n"
        + "  \"type\": \"conversational_v2\",\n"
        + "  \"memory\": { \"type\": \"agentic_memory\" },\n"
        + "  \"description\": \"agent used by RestMLDynamicToolSelectionIT\",\n"
        + "  \"tool_selection\": { \"enabled\": true, \"pinned_tools\": [\"list_index\"], \"top_k\": 3, \"min_pool_size\": 20,"
        + " \"max_dynamic_tools\": 8, \"embedding_model_id\": \"agent-embedding-model\" },\n"
        + "  \"tools\": [\n"
        + "    { \"type\": \"ListIndexTool\", \"name\": \"list_index\", \"description\": \"List the indices in the cluster.\" },\n"
        + "    { \"type\": \"IndexMappingTool\", \"name\": \"index_mapping\", \"description\": \"Inspect index mappings.\" }\n"
        + "  ]\n"
        + "}";

    @After
    public void resetToolSelectionSettings() throws IOException {
        updateClusterSettings(ENABLED_KEY, null);
        updateClusterSettings(MODEL_ID_KEY, null);
        updateClusterSettings(SEARCH_TIMEOUT_KEY, null);
    }

    @Test
    public void testToolSelectionSettingsAreAcceptedAndReadBack() throws Exception {
        updateClusterSettings(ENABLED_KEY, true);
        updateClusterSettings(MODEL_ID_KEY, "some-embedding-model-id");
        updateClusterSettings(SEARCH_TIMEOUT_KEY, "500ms");

        Response response = TestHelper.makeRequest(client(), "GET", "/_cluster/settings?flat_settings=true", null, "", null);
        Map<String, Object> persistent = (Map<String, Object>) parseResponseToMap(response).get("persistent");

        assertEquals("true", persistent.get(ENABLED_KEY).toString());
        assertEquals("some-embedding-model-id", persistent.get(MODEL_ID_KEY).toString());
        assertEquals("500ms", persistent.get(SEARCH_TIMEOUT_KEY).toString());
    }

    @Test
    public void testSettingsAreDynamicAndNeedNoRestart() throws Exception {
        // All are Property.Dynamic.
        updateClusterSettings(SEARCH_TIMEOUT_KEY, "4s");
        updateClusterSettings(SEARCH_TIMEOUT_KEY, "9s");

        Response response = TestHelper.makeRequest(client(), "GET", "/_cluster/settings?flat_settings=true", null, "", null);
        Map<String, Object> persistent = (Map<String, Object>) parseResponseToMap(response).get("persistent");
        assertEquals("9s", persistent.get(SEARCH_TIMEOUT_KEY).toString());
    }

    @Test
    public void testPerAgentKnobsAreNotClusterSettings() throws Exception {
        // Pinned tools, top_k and min_pool_size are configured per agent; the cluster rejects them.
        assertSettingRejected("plugins.ml_commons.tool_selection.pinned_tools", List.of("SearchIndexTool"));
        assertSettingRejected("plugins.ml_commons.tool_selection.top_k", 3);
        assertSettingRejected("plugins.ml_commons.tool_selection.min_pool_size", 20);
    }

    @Test
    public void testTooSmallSearchTimeoutIsRejected() throws Exception {
        assertSettingRejected(SEARCH_TIMEOUT_KEY, "10ms");
    }

    private void assertSettingRejected(String key, Object value) throws IOException {
        try {
            updateClusterSettings(key, value);
            fail("Expected " + key + "=" + value + " to be rejected");
        } catch (ResponseException e) {
            assertEquals(RestStatus.BAD_REQUEST.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAgentToolSelectionBlockRoundTripsThroughTheApi() throws Exception {
        String agentId = createAgent(AGENT_WITH_TOOL_SELECTION);

        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);
        Map<String, Object> agent = parseResponseToMap(response);
        Map<String, Object> toolSelection = (Map<String, Object>) agent.get("tool_selection");

        assertNotNull("tool_selection must survive the register -> index -> get round trip", toolSelection);
        assertEquals(true, toolSelection.get("enabled"));
        assertEquals(List.of("list_index"), toolSelection.get("pinned_tools"));
        assertEquals(3, ((Number) toolSelection.get("top_k")).intValue());
        assertEquals(20, ((Number) toolSelection.get("min_pool_size")).intValue());
        assertEquals(8, ((Number) toolSelection.get("max_dynamic_tools")).intValue());
        assertEquals("agent-embedding-model", toolSelection.get("embedding_model_id"));
    }

    @Test
    public void testAgentUpdateKeepsToolSelectionAndCanChangeIt() throws Exception {
        String agentId = createAgent(AGENT_WITH_TOOL_SELECTION);
        String path = "/_plugins/_ml/agents/" + agentId;

        TestHelper.makeRequest(client(), "PUT", path, null, "{ \"description\": \"updated description\" }", null);
        Map<String, Object> agent = parseResponseToMap(TestHelper.makeRequest(client(), "GET", path, null, "", null));
        Map<String, Object> toolSelection = (Map<String, Object>) agent.get("tool_selection");
        assertEquals("updated description", agent.get("description"));
        assertEquals(true, toolSelection.get("enabled"));
        assertEquals(List.of("list_index"), toolSelection.get("pinned_tools"));

        TestHelper.makeRequest(client(), "PUT", path, null, "{ \"tool_selection\": { \"enabled\": false, \"top_k\": 6 } }", null);
        toolSelection = (Map<String, Object>) parseResponseToMap(TestHelper.makeRequest(client(), "GET", path, null, "", null))
            .get("tool_selection");
        assertEquals(false, toolSelection.get("enabled"));
        assertEquals(6, ((Number) toolSelection.get("top_k")).intValue());
        assertEquals(List.of("list_index"), toolSelection.get("pinned_tools"));
    }

    @Test
    public void testAgentUpdateCanResetToolSelectionToTheDefaults() throws Exception {
        String agentId = createAgent(AGENT_WITH_TOOL_SELECTION);
        String path = "/_plugins/_ml/agents/" + agentId;

        // A null field goes back to the default; the stored document must lose it, not keep the old value.
        TestHelper.makeRequest(client(), "PUT", path, null, "{ \"tool_selection\": { \"embedding_model_id\": null } }", null);
        Map<String, Object> toolSelection = (Map<String, Object>) parseResponseToMap(
            TestHelper.makeRequest(client(), "GET", path, null, "", null)
        ).get("tool_selection");
        assertNull(toolSelection.get("embedding_model_id"));
        assertEquals(3, ((Number) toolSelection.get("top_k")).intValue());

        TestHelper.makeRequest(client(), "PUT", path, null, "{ \"tool_selection\": null }", null);
        assertNull(parseResponseToMap(TestHelper.makeRequest(client(), "GET", path, null, "", null)).get("tool_selection"));
    }

    @Test
    public void testToolSelectionIsRejectedOnOtherAgentTypes() throws Exception {
        String body = "{\n"
            + "  \"name\": \"tool-selection-flow-agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"tool_selection\": { \"enabled\": true },\n"
            + "  \"tools\": [ { \"type\": \"ListIndexTool\", \"name\": \"list_index\" } ]\n"
            + "}";
        try {
            createAgent(body);
            fail("Expected tool_selection on a flow agent to be rejected at registration");
        } catch (ResponseException e) {
            assertEquals(RestStatus.BAD_REQUEST.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testAgentWithPartialToolSelectionOmitsUnsetFields() throws Exception {
        String body = "{\n"
            + "  \"name\": \"tool-selection-partial-agent\",\n"
            + "  \"type\": \"conversational_v2\",\n"
            + "  \"memory\": { \"type\": \"agentic_memory\" },\n"
            + "  \"tool_selection\": { \"top_k\": 3 },\n"
            + "  \"tools\": [ { \"type\": \"ListIndexTool\", \"name\": \"list_index\" } ]\n"
            + "}";
        String agentId = createAgent(body);

        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);
        Map<String, Object> toolSelection = (Map<String, Object>) parseResponseToMap(response).get("tool_selection");

        assertEquals(3, ((Number) toolSelection.get("top_k")).intValue());
        // Unset knobs must not materialise, so a later change to a built-in default still applies.
        assertNull(toolSelection.get("enabled"));
        assertNull(toolSelection.get("pinned_tools"));
        assertNull(toolSelection.get("min_pool_size"));
        assertNull(toolSelection.get("max_dynamic_tools"));
        assertNull(toolSelection.get("embedding_model_id"));
    }

    @Test
    public void testAgentWithoutToolSelectionHasNoSuchBlock() throws Exception {
        // Existing agents must serialize exactly as they did before this feature.
        String body = "{\n"
            + "  \"name\": \"tool-selection-absent-agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"tools\": [ { \"type\": \"ListIndexTool\", \"name\": \"list_index\" } ]\n"
            + "}";
        String agentId = createAgent(body);

        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);
        assertNull(parseResponseToMap(response).get("tool_selection"));
    }

    @Test
    public void testInvalidAgentToolSelectionIsRejected() throws Exception {
        assertAgentRejected("{ \"top_k\": 0 }");
        assertAgentRejected("{ \"min_pool_size\": 0 }");
        assertAgentRejected("{ \"pinned_tools\": [\"\"] }");
        assertAgentRejected("{ \"max_dynamic_tools\": 0 }");
        assertAgentRejected("{ \"max_dynamic_tools\": 101 }");
        assertAgentRejected("{ \"top_k\": 51 }");
        assertAgentRejected("{ \"embedding_model_id\": \"\" }");
    }

    private void assertAgentRejected(String toolSelectionJson) throws IOException, ParseException {
        String body = "{\n"
            + "  \"name\": \"tool-selection-invalid-agent\",\n"
            + "  \"type\": \"conversational_v2\",\n"
            + "  \"memory\": { \"type\": \"agentic_memory\" },\n"
            + "  \"tool_selection\": "
            + toolSelectionJson
            + ",\n"
            + "  \"tools\": [ { \"type\": \"ListIndexTool\", \"name\": \"list_index\" } ]\n"
            + "}";
        try {
            createAgent(body);
            fail("Expected tool_selection " + toolSelectionJson + " to be rejected at registration");
        } catch (ResponseException e) {
            assertEquals(RestStatus.BAD_REQUEST.getStatus(), e.getResponse().getStatusLine().getStatusCode());
        }
    }

}
