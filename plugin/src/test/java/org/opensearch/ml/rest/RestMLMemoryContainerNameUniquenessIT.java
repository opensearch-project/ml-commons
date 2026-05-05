/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

/**
 * End-to-end integration tests for the memory-container-name-uniqueness feature gated by
 * {@code plugins.ml_commons.agentic_memory_name_uniqueness_enabled}.
 */
public class RestMLMemoryContainerNameUniquenessIT extends MLCommonsRestTestCase {

    private static final String SETTING_KEY = "plugins.ml_commons.agentic_memory_name_uniqueness_enabled";

    @Before
    public void setupUniquenessSetting() throws IOException {
        updateClusterSettings(SETTING_KEY, false);
    }

    @After
    public void resetUniquenessSetting() throws IOException {
        updateClusterSettings(SETTING_KEY, false);
    }

    public void testDuplicateAllowed_WhenFlagOff() throws IOException {
        String body = createMemoryContainerBody("it-memcontainer-flag-off");

        Response r1 = createMemoryContainer(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        Response r2 = createMemoryContainer(body);
        assertEquals(200, r2.getStatusLine().getStatusCode());

        String id1 = (String) parseResponseToMap(r1).get("memory_container_id");
        String id2 = (String) parseResponseToMap(r2).get("memory_container_id");
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals("Duplicate names with flag off should still produce distinct container IDs", id1, id2);
    }

    public void testDuplicateRejected_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        String body = createMemoryContainerBody("it-memcontainer-flag-on");

        Response r1 = createMemoryContainer(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        try {
            createMemoryContainer(body);
            fail("Expected duplicate memory container creation to be rejected with 409 when uniqueness flag is on");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
            String payload = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "409 response should cite the duplicate name, got: " + payload,
                payload.contains("already exists") && payload.contains("it-memcontainer-flag-on")
            );
        }
    }

    public void testUniqueNameAccepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = createMemoryContainer(createMemoryContainerBody("it-memcontainer-unique-" + System.nanoTime()));
        assertEquals(200, r.getStatusLine().getStatusCode());
        assertNotNull(parseResponseToMap(r).get("memory_container_id"));
    }

    public void testDynamicFlip_OnThenOff() throws IOException {
        String body = createMemoryContainerBody("it-memcontainer-dynamic-flip");

        updateClusterSettings(SETTING_KEY, true);
        Response r1 = createMemoryContainer(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        try {
            createMemoryContainer(body);
            fail("Expected 409 while flag is on");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
        }

        updateClusterSettings(SETTING_KEY, false);
        Response r2 = createMemoryContainer(body);
        assertEquals("Duplicate must be accepted again once flag is flipped off", 200, r2.getStatusLine().getStatusCode());
    }

    public void testRename_ToUnusedName_Accepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = createMemoryContainer(createMemoryContainerBody("it-memc-rename-src"));
        String id = (String) parseResponseToMap(r).get("memory_container_id");
        assertNotNull(id);

        Response renamed = updateMemoryContainer(id, "{\"name\":\"it-memc-rename-dst-" + System.nanoTime() + "\"}");
        assertEquals(200, renamed.getStatusLine().getStatusCode());
    }

    public void testRename_ToExistingName_Rejected_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response first = createMemoryContainer(createMemoryContainerBody("it-memc-rename-existing"));
        assertEquals(200, first.getStatusLine().getStatusCode());
        String firstId = (String) parseResponseToMap(first).get("memory_container_id");
        assertNotNull(firstId);

        Response second = createMemoryContainer(createMemoryContainerBody("it-memc-rename-loser"));
        String secondId = (String) parseResponseToMap(second).get("memory_container_id");
        assertNotNull(secondId);

        try {
            updateMemoryContainer(secondId, "{\"name\":\"it-memc-rename-existing\"}");
            fail("Expected rename to an existing name to be rejected with 409");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
            String payload = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "409 response should cite the duplicate name, got: " + payload,
                payload.contains("already exists") && payload.contains("it-memc-rename-existing")
            );
            assertFalse("409 response must not leak the conflicting memory_container_id, got: " + payload, payload.contains(firstId));
        }
    }

    public void testRename_BlankName_NoOp_Accepted_WhenFlagOn() throws IOException {
        // A PUT with a blank name must be treated as "no rename" (mirrors the StringUtils.isNotBlank
        // gate in TransportUpdateModelGroupAction.updateModelGroup) - must not 409.
        updateClusterSettings(SETTING_KEY, true);

        Response r = createMemoryContainer(createMemoryContainerBody("it-memc-rename-blank-src"));
        String id = (String) parseResponseToMap(r).get("memory_container_id");
        assertNotNull(id);

        Response blank = updateMemoryContainer(id, "{\"name\":\"   \"}");
        assertEquals(200, blank.getStatusLine().getStatusCode());
    }

    public void testRename_SameName_NoOp_Accepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = createMemoryContainer(createMemoryContainerBody("it-memc-rename-self"));
        String id = (String) parseResponseToMap(r).get("memory_container_id");
        assertNotNull(id);

        Response same = updateMemoryContainer(id, "{\"name\":\"it-memc-rename-self\"}");
        assertEquals(200, same.getStatusLine().getStatusCode());
    }

    public void testRename_ToExistingName_Allowed_WhenFlagOff() throws IOException {
        // Flag stays off (default from @Before). Rename onto an existing name must succeed (BWC).
        Response first = createMemoryContainer(createMemoryContainerBody("it-memc-rename-bwc"));
        assertEquals(200, first.getStatusLine().getStatusCode());

        Response second = createMemoryContainer(createMemoryContainerBody("it-memc-rename-bwc-other"));
        String secondId = (String) parseResponseToMap(second).get("memory_container_id");

        Response renamed = updateMemoryContainer(secondId, "{\"name\":\"it-memc-rename-bwc\"}");
        assertEquals(200, renamed.getStatusLine().getStatusCode());
    }

    private static Response createMemoryContainer(String body) throws IOException {
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/memory_containers/_create", null, TestHelper.toHttpEntity(body), null);
    }

    private static Response updateMemoryContainer(String id, String body) throws IOException {
        return TestHelper.makeRequest(client(), "PUT", "/_plugins/_ml/memory_containers/" + id, null, TestHelper.toHttpEntity(body), null);
    }

    private static String createMemoryContainerBody(String name) {
        return "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"description\": \"uniqueness IT memory container\",\n"
            + "  \"configuration\": {}\n"
            + "}";
    }
}
