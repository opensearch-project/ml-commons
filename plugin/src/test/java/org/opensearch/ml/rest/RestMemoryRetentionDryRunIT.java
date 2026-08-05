/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.utils.TestHelper;

/**
 * Integration tests for the retention dry-run endpoints, which previously had zero IT coverage:
 * <ul>
 *   <li>{@code POST /_plugins/_ml/memory_containers/{id}/_retention/_dry_run} (single container)</li>
 *   <li>{@code POST /_plugins/_ml/memory_containers/_retention/_dry_run} (cluster-wide array)</li>
 * </ul>
 *
 * <p>All tests use BARE containers ({@code "configuration": {}}) so sessions can be created without any
 * external LLM/embedding credentials; the dry-run computation reuses the retention job's read-only
 * count logic and never needs a model.
 *
 * <p>The critical safety assertion (see {@link #testDryRunPerformsNoDeletion()}) proves the dry-run
 * deletes nothing: session documents created before the dry-run still exist afterward.
 */
public class RestMemoryRetentionDryRunIT extends MLCommonsRestTestCase {

    private static final String CREATE_PATH = "/_plugins/_ml/memory_containers/_create";
    private static final String CONTAINER_PATH = "/_plugins/_ml/memory_containers/";
    private static final String CLUSTER_WIDE_DRY_RUN_PATH = "/_plugins/_ml/memory_containers/_retention/_dry_run";
    private static final String DEFAULT_SESSION_MAX_COUNT_SETTING = "plugins.ml_commons.memory.default_session_max_count";

    @Before
    public void setup() throws IOException {
        updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
        updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testSingleContainerDryRunShapeAndStoredPolicySource() throws IOException {
        String containerId = createContainer(
            "{ \"name\": \"dryrun_shape_it_"
                + System.currentTimeMillis()
                + "\", \"configuration\": { \"retention_policy\": { \"sessions\": { \"max_count\": 5 } } } }"
        );

        Map<String, Object> result = dryRunSingle(containerId);

        // Top-level documented shape.
        assertEquals(containerId, result.get("container_id"));
        assertTrue("evaluated_at should be a numeric epoch millis", result.get("evaluated_at") instanceof Number);
        assertEquals("policy_source should be 'stored' when the container has its own policy", "stored", result.get("policy_source"));
        assertTrue("effective_policy should be an object", result.get("effective_policy") instanceof Map);

        // would_delete carries all four memory-type buckets, each an object with total + by_reason.
        Map<String, Object> wouldDelete = (Map<String, Object>) result.get("would_delete");
        assertNotNull("would_delete must be present", wouldDelete);
        for (String key : List.of("sessions", "long_term", "history", "working_memory")) {
            Map<String, Object> bucket = (Map<String, Object>) wouldDelete.get(key);
            assertNotNull("would_delete." + key + " must be present", bucket);
            assertTrue("would_delete." + key + ".total must be numeric", bucket.get("total") instanceof Number);
            assertTrue("would_delete." + key + ".by_reason must be an object", bucket.get("by_reason") instanceof Map);
        }

        assertTrue("total_would_delete must be numeric", result.get("total_would_delete") instanceof Number);
        assertTrue("pinned_would_skip must be numeric", result.get("pinned_would_skip") instanceof Number);
        assertTrue("warnings must be a list", result.get("warnings") instanceof List);

        // effective_policy should echo the stored sessions rule.
        Map<String, Object> effectivePolicy = (Map<String, Object>) result.get("effective_policy");
        Map<String, Object> sessions = (Map<String, Object>) effectivePolicy.get("sessions");
        assertNotNull("effective_policy.sessions should reflect the stored rule", sessions);
        assertEquals(5, ((Number) sessions.get("max_count")).intValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDryRunPolicySourceNoneWhenNoPolicyAndNoDefaults() throws IOException {
        // Bare container with no retention_policy and no cluster defaults configured (all default to -1).
        String containerId = createContainer("{ \"name\": \"dryrun_none_it_" + System.currentTimeMillis() + "\", \"configuration\": {} }");

        Map<String, Object> result = dryRunSingle(containerId);

        assertEquals("policy_source should be 'none' with no stored policy and no cluster defaults", "none", result.get("policy_source"));
        assertEquals("nothing should be reported for deletion", 0, ((Number) result.get("total_would_delete")).intValue());
        List<String> warnings = (List<String>) result.get("warnings");
        assertTrue("a 'no retention policy' warning should be emitted", warnings.stream().anyMatch(w -> w.contains("no retention policy")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDryRunPolicySourceDefaultWhenClusterDefaultsConfigured() throws IOException {
        // Bare container with NO stored policy; a configured cluster default makes the effective source "default".
        String containerId = createContainer(
            "{ \"name\": \"dryrun_default_it_" + System.currentTimeMillis() + "\", \"configuration\": {} }"
        );
        try {
            updateClusterSettings(DEFAULT_SESSION_MAX_COUNT_SETTING, 10);

            Map<String, Object> result = dryRunSingle(containerId);
            assertEquals("policy_source should be 'default' when cluster defaults apply", "default", result.get("policy_source"));
            Map<String, Object> effectivePolicy = (Map<String, Object>) result.get("effective_policy");
            Map<String, Object> sessions = (Map<String, Object>) effectivePolicy.get("sessions");
            assertNotNull("effective_policy.sessions should be backfilled from the cluster default", sessions);
            assertEquals(10, ((Number) sessions.get("max_count")).intValue());
        } finally {
            // Restore the unset sentinel so this default cannot leak into other tests via persistent settings.
            updateClusterSettings(DEFAULT_SESSION_MAX_COUNT_SETTING, -1);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDryRunPerformsNoDeletion() throws IOException {
        // Aggressive policy so the dry-run WOULD report deletions; the safety assertion is that it does not delete.
        String containerId = createContainer(
            "{ \"name\": \"dryrun_safety_it_"
                + System.currentTimeMillis()
                + "\", \"configuration\": { \"retention_policy\": { \"sessions\": { \"max_count\": 1 } } } }"
        );

        createSession(containerId, "safety-1");
        createSession(containerId, "safety-2");
        createSession(containerId, "safety-3");
        assertEquals("precondition: 3 sessions exist", 3L, countSessions(containerId));

        Map<String, Object> result = dryRunSingle(containerId);
        Map<String, Object> wouldDelete = (Map<String, Object>) result.get("would_delete");
        Map<String, Object> sessions = (Map<String, Object>) wouldDelete.get("sessions");
        // 3 non-pinned sessions, max_count 1 -> the job would delete the 2 oldest.
        assertEquals("dry-run should report 2 sessions would be deleted", 2, ((Number) sessions.get("total")).intValue());

        // CRITICAL: the dry-run must not have deleted anything.
        assertEquals("dry-run must delete nothing; all 3 sessions must still exist", 3L, countSessions(containerId));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testClusterWideDryRunReturnsArrayIncludingCreatedContainer() throws IOException {
        String containerId = createContainer(
            "{ \"name\": \"dryrun_all_it_"
                + System.currentTimeMillis()
                + "\", \"configuration\": { \"retention_policy\": { \"sessions\": { \"max_count\": 3 } } } }"
        );

        Map<String, Object> response = dryRunClusterWide();
        List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
        assertNotNull("cluster-wide dry-run should wrap results in a \"results\" array", results);
        assertFalse("cluster-wide dry-run should return a non-empty array", results.isEmpty());
        assertTrue(
            "cluster-wide dry-run should include the created container",
            results.stream().anyMatch(r -> containerId.equals(r.get("container_id")))
        );
        // fix #3: the cluster-wide response surfaces a skipped_count for containers dropped during evaluation.
        assertNotNull("cluster-wide dry-run should surface a skipped_count field", response.get("skipped_count"));
        assertEquals("no containers should be skipped in this happy-path scenario", 0, ((Number) response.get("skipped_count")).intValue());
    }

    @Test
    public void testDryRunNonExistentContainerReturns404() throws IOException {
        // Ensure the container index exists first so this exercises the "container not found" path (404),
        // not an index-not-found path.
        createContainer("{ \"name\": \"dryrun_404_seed_" + System.currentTimeMillis() + "\", \"configuration\": {} }");

        try {
            TestHelper
                .makeRequest(
                    client(),
                    "POST",
                    CONTAINER_PATH + "does-not-exist-" + System.currentTimeMillis() + "/_retention/_dry_run",
                    null,
                    "",
                    null
                );
            fail("dry-run against a non-existent container id should fail, not return 200");
        } catch (ResponseException e) {
            assertEquals("a missing container should yield 404, not 500", 404, e.getResponse().getStatusLine().getStatusCode());
        }
    }

    @Test
    public void testDryRunForbiddenWhenAgenticMemoryDisabled() throws IOException {
        String containerId = createContainer(
            "{ \"name\": \"dryrun_disabled_it_" + System.currentTimeMillis() + "\", \"configuration\": {} }"
        );
        try {
            updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", false);
            try {
                TestHelper.makeRequest(client(), "POST", CONTAINER_PATH + containerId + "/_retention/_dry_run", null, "", null);
                fail("dry-run should be rejected with 403 when agentic memory is disabled");
            } catch (ResponseException e) {
                assertEquals(403, e.getResponse().getStatusLine().getStatusCode());
            }
        } finally {
            updateClusterSettings("plugins.ml_commons.agentic_memory_enabled", true);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDryRunStillComputesButWarnsWhenRetentionDisabled() throws IOException {
        // Create the container WHILE retention is enabled (a stored policy is rejected when disabled), then disable.
        String containerId = createContainer(
            "{ \"name\": \"dryrun_retdisabled_it_"
                + System.currentTimeMillis()
                + "\", \"configuration\": { \"retention_policy\": { \"sessions\": { \"max_count\": 2 } } } }"
        );
        try {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", false);

            // Dry-run still returns 200 and still computes; it warns that the scheduled job will not run.
            Map<String, Object> result = dryRunSingle(containerId);
            assertEquals("stored", result.get("policy_source"));
            List<String> warnings = (List<String>) result.get("warnings");
            assertTrue(
                "a retention-disabled warning should be emitted while still computing",
                warnings.stream().anyMatch(w -> w.contains("retention is disabled cluster-wide"))
            );
        } finally {
            updateClusterSettings("plugins.ml_commons.memory.retention_enabled", true);
        }
    }

    // ---- helpers ----

    private String createContainer(String body) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", CREATE_PATH, null, TestHelper.toHttpEntity(body), null);
        assertEquals(200, response.getStatusLine().getStatusCode());
        String containerId = (String) parseResponseToMap(response).get("memory_container_id");
        assertNotNull("create should return a memory_container_id", containerId);
        return containerId;
    }

    private void createSession(String containerId, String sessionId) throws IOException {
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
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dryRunSingle(String containerId) throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", CONTAINER_PATH + containerId + "/_retention/_dry_run", null, "", null);
        assertEquals(200, response.getStatusLine().getStatusCode());
        return parseResponseToMap(response);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dryRunClusterWide() throws IOException {
        Response response = TestHelper.makeRequest(client(), "POST", CLUSTER_WIDE_DRY_RUN_PATH, null, "", null);
        assertEquals(200, response.getStatusLine().getStatusCode());
        String body = TestHelper.httpEntityToString(response.getEntity());
        return StringUtils.gson.fromJson(body, Map.class);
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
}
