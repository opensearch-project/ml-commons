/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for the on-demand retention execute endpoint {@code POST /_plugins/_ml/memory_containers/_retention/_execute},
 * which previously had zero IT coverage.
 *
 * <p>The highest-value test ({@link #testExecuteEvictsExpiredSessions()}) is a true end-to-end eviction: it uses a
 * count-based ({@code max_count}) session policy, which is deterministic and requires NO external LLM/embedding model,
 * so it runs on the credential-less ephemeral node. Because the retention pipeline is fully async, the eviction
 * assertion polls via {@code assertBusy(...)} with a generous but bounded timeout rather than asserting immediately.
 */
public class RestMemoryRetentionExecuteIT extends MLCommonsRestTestCase {

    private static final String CREATE_PATH = "/_plugins/_ml/memory_containers/_create";
    private static final String CONTAINER_PATH = "/_plugins/_ml/memory_containers/";
    private static final String EXECUTE_PATH = "/_plugins/_ml/memory_containers/_retention/_execute";

    @Before
    public void setup() throws IOException {
        updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
        updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
    }

    @Test
    public void testExecuteReturnsTriggeredWhenEnabled() throws Exception {
        // The retention job is a cluster-wide singleton with an in-progress guard, so a run kicked off by an
        // earlier test in this suite may still hold the guard and make a fresh trigger return "already_running".
        // Poll until we observe a genuine "triggered" so the assertion is order-independent, not flaky.
        Map<String, Object> body = triggerUntilAccepted();
        assertEquals("triggered", body.get("status"));
        assertEquals(Boolean.TRUE, body.get("triggered"));
        assertEquals(Boolean.TRUE, body.get("acknowledged"));
    }

    @Test
    public void testExecuteReturnsRetentionDisabledWhenDisabled() throws IOException {
        try {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", false);
            Map<String, Object> body = execute();
            // Reads TransportExecuteMemoryRetentionAction / TriggerStatus: disabled is a benign HTTP 200 status,
            // not an error, with triggered=false.
            assertEquals("retention_disabled", body.get("status"));
            assertEquals(Boolean.FALSE, body.get("triggered"));
            // acknowledged mirrors whether a run was actually triggered; a disabled job was not, so it is false.
            assertEquals(Boolean.FALSE, body.get("acknowledged"));
        } finally {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
        }
    }

    @Test
    public void testExecuteForbiddenWhenAgenticMemoryDisabled() throws IOException {
        try {
            updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", false);
            try {
                TestHelper.makeRequest(client(), "POST", EXECUTE_PATH, null, "", null);
                fail("execute should be rejected with 403 when agentic memory is disabled");
            } catch (ResponseException e) {
                assertEquals(403, e.getResponse().getStatusLine().getStatusCode());
            }
        } finally {
            updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
        }
    }

    @Test
    public void testExecuteEvictsExpiredSessions() throws Exception {
        // max_count=1, count-based eviction: deterministic and model-free. 3 non-pinned + 1 pinned session.
        String containerId = createContainer(
            "{ \"name\": \"execute_evict_it_"
                + System.currentTimeMillis()
                + "\", \"configuration\": { \"retention_policy\": { \"sessions\": { \"max_count\": 1 } } } }"
        );

        createSession(containerId, "evict-1");
        createSession(containerId, "evict-2");
        createSession(containerId, "evict-3");
        String pinnedSessionId = createSession(containerId, "evict-pinned");
        pinSession(containerId, pinnedSessionId);

        assertEquals("precondition: 4 sessions exist (3 non-pinned + 1 pinned)", 4L, countSessions(containerId));

        // Poll for a genuine trigger (an earlier test's async run may still hold the singleton guard).
        Map<String, Object> body = triggerUntilAccepted();
        assertEquals("triggered", body.get("status"));

        // Count-based eviction keeps max_count (1) most-recent non-pinned sessions and never touches pinned ones,
        // so the surviving count converges to 2: the 1 retained non-pinned + the 1 pinned. The pipeline is async,
        // so poll with a generous but bounded timeout rather than asserting immediately.
        assertBusy(() -> {
            long remaining = countSessions(containerId);
            assertEquals("expected eviction down to (max_count=1) + (1 pinned) = 2 surviving sessions", 2L, remaining);
        }, 60, TimeUnit.SECONDS);

        // The pinned session must be one of the survivors.
        assertTrue("pinned session must survive eviction", sessionExists(containerId, pinnedSessionId));
    }

    @Test
    public void testConcurrentExecuteDoesNotError() throws IOException {
        // A second immediate trigger must return cleanly (either "triggered" again or "already_running"),
        // never an HTTP error. Both are benign HTTP 200 outcomes. acknowledged mirrors triggered: it is true
        // only when this call actually started a run, and false for a benign "already_running".
        Map<String, Object> first = execute();
        assertBenignExecuteOutcome(first);

        Map<String, Object> second = execute();
        assertBenignExecuteOutcome(second);
    }

    // ---- helpers ----

    /**
     * Asserts an execute response is a benign HTTP 200 outcome ("triggered" or "already_running", never an error)
     * and that {@code acknowledged} agrees with {@code triggered}: true only when a run was actually started.
     */
    private void assertBenignExecuteOutcome(Map<String, Object> body) {
        Object status = body.get("status");
        assertTrue("execute should report a benign status, not an error", List.of("triggered", "already_running").contains(status));
        assertEquals("acknowledged must mirror triggered", body.get("triggered"), body.get("acknowledged"));
    }

    private String createContainer(String body) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
        assertEquals(200, response.getStatusLine().getStatusCode());
        String containerId = (String) parseResponseToMap(response).get("memory_container_id");
        assertNotNull("create should return a memory_container_id", containerId);
        return containerId;
    }

    private String createSession(String containerId, String sessionId) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                CONTAINER_PATH + containerId + "/memories/sessions",
                null,
                TestHelper.toHttpEntity("{ \"session_id\": \"" + sessionId + "\" }"),
                null
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
        String id = (String) parseResponseToMap(response).get("session_id");
        assertNotNull("create session should return a session_id", id);
        return id;
    }

    private void pinSession(String containerId, String sessionId) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                CONTAINER_PATH + containerId + "/memories/sessions/" + sessionId,
                null,
                TestHelper.toHttpEntity("{ \"pinned\": true }"),
                null
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
    }

    private Map<String, Object> execute() throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", EXECUTE_PATH, null, "", null);
        assertEquals(200, response.getStatusLine().getStatusCode());
        return parseResponseToMap(response);
    }

    /**
     * Triggers the retention job, retrying while a prior (possibly cross-test) run still holds the cluster-wide
     * singleton guard and returns {@code already_running}. Returns the response once the trigger is genuinely
     * accepted ({@code triggered}). Makes the "triggered" assertion independent of randomized test order.
     */
    private Map<String, Object> triggerUntilAccepted() throws Exception {
        Map<String, Object>[] last = new Map[1];
        assertBusy(() -> {
            Map<String, Object> body = execute();
            last[0] = body;
            assertEquals("waiting for a prior retention run to release the guard", "triggered", body.get("status"));
        }, 60, TimeUnit.SECONDS);
        return last[0];
    }

    @SuppressWarnings("unchecked")
    private long countSessions(String containerId) throws IOException {
        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                CONTAINER_PATH + containerId + "/memories/sessions/_search",
                null,
                TestHelper.toHttpEntity("{ \"size\": 0, \"track_total_hits\": true, \"query\": { \"match_all\": {} } }"),
                null
            );
        assertEquals(200, response.getStatusLine().getStatusCode());
        Map<String, Object> map = parseResponseToMap(response);
        Map<String, Object> hits = (Map<String, Object>) map.get("hits");
        Map<String, Object> total = (Map<String, Object>) hits.get("total");
        return ((Number) total.get("value")).longValue();
    }

    private boolean sessionExists(String containerId, String sessionId) throws IOException {
        try {
            Response response = TestHelper
                .makeRequest(client(), "GET", CONTAINER_PATH + containerId + "/memories/sessions/" + sessionId, null, "", null);
            return response.getStatusLine().getStatusCode() == 200;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
}
