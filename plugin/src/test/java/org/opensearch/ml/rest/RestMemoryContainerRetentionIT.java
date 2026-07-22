/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for the memory container retention_policy data-model wiring
 * (PR 1: data model + API). Exercises the create -> get round-trip of a
 * retention_policy, an explicit "retention_policy": null wipe on update, and
 * validation of the constraints enforced by {@code MemoryConfiguration}.
 */
public class RestMemoryContainerRetentionIT extends MLCommonsRestTestCase {

    private static final String CREATE_PATH = "/_plugins/_ml/memory_containers/_create";
    private static final String CONTAINER_PATH = "/_plugins/_ml/memory_containers/";

    @Before
    public void setup() throws IOException {
        updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
        // Retention is disabled by default (opt-in kill switch); enable it explicitly so these round-trip tests exercise the feature.
        updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
    }

    @Test
    public void testCreateContainerWithRetentionPolicyRoundTrip() throws IOException {
        String body = "{\n"
            + "  \"name\": \"retention_it_"
            + System.currentTimeMillis()
            + "\",\n"
            + "  \"description\": \"retention round-trip\",\n"
            + "  \"configuration\": {\n"
            + "    \"retention_policy\": {\n"
            + "      \"sessions\": { \"retention_days\": 30, \"max_count\": 100 },\n"
            + "      \"long-term\": { \"retention_days\": 90 },\n"
            + "      \"history\": { \"max_count\": 50 }\n"
            + "    }\n"
            + "  }\n"
            + "}";

        String containerId = createContainer(body);

        Map<String, Object> config = getConfiguration(containerId);
        Map<String, Object> retentionPolicy = asMap(config.get("retention_policy"));
        assertNotNull("retention_policy should be persisted", retentionPolicy);

        Map<String, Object> sessions = asMap(retentionPolicy.get("sessions"));
        assertEquals(30, ((Number) sessions.get("retention_days")).intValue());
        assertEquals(100, ((Number) sessions.get("max_count")).intValue());

        Map<String, Object> longTerm = asMap(retentionPolicy.get("long-term"));
        assertEquals(90, ((Number) longTerm.get("retention_days")).intValue());

        Map<String, Object> history = asMap(retentionPolicy.get("history"));
        assertEquals(50, ((Number) history.get("max_count")).intValue());
        assertFalse("history must not carry retention_days", history.containsKey("retention_days"));
    }

    @Test
    public void testUpdateContainerWipesRetentionPolicyWithExplicitNull() throws IOException {
        String createBody = "{\n"
            + "  \"name\": \"retention_wipe_it_"
            + System.currentTimeMillis()
            + "\",\n"
            + "  \"configuration\": {\n"
            + "    \"retention_policy\": {\n"
            + "      \"sessions\": { \"retention_days\": 30 }\n"
            + "    }\n"
            + "  }\n"
            + "}";
        String containerId = createContainer(createBody);
        assertNotNull(getConfiguration(containerId).get("retention_policy"));

        // Explicit null must wipe the existing policy, not preserve it.
        String updateBody = "{ \"configuration\": { \"retention_policy\": null } }";
        Response updateResponse = TestHelper
            .makeRequest(client(), "PUT", CONTAINER_PATH + containerId, null, TestHelper.toHttpEntity(updateBody), null);
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        Map<String, Object> config = getConfiguration(containerId);
        assertNull("retention_policy should be wiped after explicit null update", config.get("retention_policy"));
    }

    @Test
    public void testCreateContainerRejectsWorkingRetention() throws IOException {
        String body = "{\n"
            + "  \"name\": \"retention_working_it_"
            + System.currentTimeMillis()
            + "\",\n"
            + "  \"configuration\": {\n"
            + "    \"retention_policy\": { \"working\": { \"retention_days\": 5 } }\n"
            + "  }\n"
            + "}";
        try {
            TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
            fail("Configuring working memory retention directly should be rejected");
        } catch (org.opensearch.client.ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testCreateContainerRejectsHistoryRetentionDays() throws IOException {
        String body = "{\n"
            + "  \"name\": \"retention_history_it_"
            + System.currentTimeMillis()
            + "\",\n"
            + "  \"configuration\": {\n"
            + "    \"retention_policy\": { \"history\": { \"retention_days\": 5 } }\n"
            + "  }\n"
            + "}";
        try {
            TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
            fail("retention_days on history memory should be rejected");
        } catch (org.opensearch.client.ResponseException e) {
            assertEquals(400, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testRetentionPolicyRejectedWhenFeatureDisabled() throws IOException {
        try {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", false);
            String body = "{\n"
                + "  \"name\": \"retention_disabled_it_"
                + System.currentTimeMillis()
                + "\",\n"
                + "  \"configuration\": {\n"
                + "    \"retention_policy\": { \"sessions\": { \"retention_days\": 30 } }\n"
                + "  }\n"
                + "}";
            try {
                TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
                fail("retention_policy should be rejected when the retention feature is disabled");
            } catch (org.opensearch.client.ResponseException e) {
                assertEquals(403, e.getResponse().getStatusLine().getStatusCode());
            }
        } finally {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
        }
    }

    @Test
    public void testPinnedRoundTripOnSession() throws IOException {
        // Bare container (no LLM/embedding) so sessions work without external credentials.
        String containerId = createContainer("{ \"name\": \"pinned_it_" + System.currentTimeMillis() + "\", \"configuration\": {} }");

        // Create a real session document via the dedicated create-session endpoint (no LLM/embedding needed;
        // POST /memories with a client-supplied session_id does NOT persist a session doc, only working memory).
        String sessionBody = "{ \"session_id\": \"pinned-session-1\" }";
        Response addResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                CONTAINER_PATH + containerId + "/memories/sessions",
                null,
                TestHelper.toHttpEntity(sessionBody),
                null
            );
        assertEquals(200, addResponse.getStatusLine().getStatusCode());
        String sessionId = (String) parseResponseToMap(addResponse).get("session_id");
        assertNotNull("create session should return a session_id", sessionId);

        // pinned is not valid on working memory; set it on the session (an allowed type).
        String pinBody = "{ \"pinned\": true }";
        Response pinResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                CONTAINER_PATH + containerId + "/memories/sessions/" + sessionId,
                null,
                TestHelper.toHttpEntity(pinBody),
                null
            );
        assertEquals(200, pinResponse.getStatusLine().getStatusCode());

        // Get the session back and confirm pinned persisted.
        Response getResponse = TestHelper
            .makeRequest(client(), "GET", CONTAINER_PATH + containerId + "/memories/sessions/" + sessionId, null, "", null);
        assertEquals(200, getResponse.getStatusLine().getStatusCode());
        Map<String, Object> session = parseResponseToMap(getResponse);
        assertEquals(Boolean.TRUE, session.get("pinned"));
    }

    @Test
    public void testPinnedRejectedWhenFeatureDisabled() throws IOException {
        // Bare container so sessions work without external credentials.
        String containerId = createContainer(
            "{ \"name\": \"pinned_disabled_it_" + System.currentTimeMillis() + "\", \"configuration\": {} }"
        );

        // Create a real session document (retention still enabled here) so the PUT below reaches the retention gate.
        String sessionBody = "{ \"session_id\": \"pinned-disabled-session-1\" }";
        Response addResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                CONTAINER_PATH + containerId + "/memories/sessions",
                null,
                TestHelper.toHttpEntity(sessionBody),
                null
            );
        assertEquals(200, addResponse.getStatusLine().getStatusCode());
        String sessionId = (String) parseResponseToMap(addResponse).get("session_id");
        assertNotNull("create session should return a session_id", sessionId);

        try {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", false);
            String pinBody = "{ \"pinned\": true }";
            try {
                TestHelper
                    .makeRequest(
                        client(),
                        "PUT",
                        CONTAINER_PATH + containerId + "/memories/sessions/" + sessionId,
                        null,
                        TestHelper.toHttpEntity(pinBody),
                        null
                    );
                fail("pinning a session should be rejected when the retention feature is disabled");
            } catch (org.opensearch.client.ResponseException e) {
                assertEquals(403, e.getResponse().getStatusLine().getStatusCode());
            }
        } finally {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
        }
    }

    private String createContainer(String body) throws IOException {
        Response createResponse = TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
        assertEquals(200, createResponse.getStatusLine().getStatusCode());
        Map<String, Object> createMap = parseResponseToMap(createResponse);
        String containerId = (String) createMap.get("memory_container_id");
        assertNotNull("create should return a memory_container_id", containerId);
        return containerId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getConfiguration(String containerId) throws IOException {
        Response getResponse = TestHelper.makeRequest(client(), "GET", CONTAINER_PATH + containerId, null, "", null);
        assertEquals(200, getResponse.getStatusLine().getStatusCode());
        Map<String, Object> getMap = parseResponseToMap(getResponse);
        return asMap(getMap.get("configuration"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object obj) {
        return (Map<String, Object>) obj;
    }
}
