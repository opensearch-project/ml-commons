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

    private static Response createMemoryContainer(String body) throws IOException {
        return TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/memory_containers/_create", null, TestHelper.toHttpEntity(body), null);
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
