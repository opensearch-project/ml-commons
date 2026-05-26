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
 * End-to-end integration tests for the agent-name-uniqueness feature gated by
 * {@code plugins.ml_commons.agent_name_uniqueness_enabled}.
 */
public class RestMLAgentNameUniquenessIT extends MLCommonsRestTestCase {

    private static final String SETTING_KEY = "plugins.ml_commons.agent_name_uniqueness_enabled";

    @Before
    public void setupUniquenessSetting() throws IOException {
        // Start every test with the flag explicitly off so tests are independent of persisted state.
        updateClusterSettings(SETTING_KEY, false);
    }

    @After
    public void resetUniquenessSetting() throws IOException {
        updateClusterSettings(SETTING_KEY, false);
    }

    public void testDuplicateAllowed_WhenFlagOff() throws IOException {
        String body = registerAgentBody("it-agent-flag-off");

        Response r1 = registerAgent(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        Response r2 = registerAgent(body);
        assertEquals(200, r2.getStatusLine().getStatusCode());

        String id1 = (String) parseResponseToMap(r1).get("agent_id");
        String id2 = (String) parseResponseToMap(r2).get("agent_id");
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals("Duplicate names with flag off should still produce distinct agent IDs", id1, id2);
    }

    public void testDuplicateRejected_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        String body = registerAgentBody("it-agent-flag-on");

        Response r1 = registerAgent(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        try {
            registerAgent(body);
            fail("Expected duplicate registration to be rejected with 409 when uniqueness flag is on");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
            String payload = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "409 response should cite the duplicate name, got: " + payload,
                payload.contains("already exists") && payload.contains("it-agent-flag-on")
            );
        }
    }

    public void testUniqueNameAccepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = registerAgent(registerAgentBody("it-agent-unique-" + System.nanoTime()));
        assertEquals(200, r.getStatusLine().getStatusCode());
        assertNotNull(parseResponseToMap(r).get("agent_id"));
    }

    public void testDynamicFlip_OnThenOff() throws IOException {
        String body = registerAgentBody("it-agent-dynamic-flip");

        updateClusterSettings(SETTING_KEY, true);
        Response r1 = registerAgent(body);
        assertEquals(200, r1.getStatusLine().getStatusCode());

        try {
            registerAgent(body);
            fail("Expected 409 while flag is on");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
        }

        // Flip off; duplicate should now be accepted again without restart.
        updateClusterSettings(SETTING_KEY, false);
        Response r2 = registerAgent(body);
        assertEquals("Duplicate must be accepted again once flag is flipped off", 200, r2.getStatusLine().getStatusCode());
    }

    public void testRename_ToUnusedName_Accepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = registerAgent(registerAgentBody("it-agent-rename-src"));
        String agentId = (String) parseResponseToMap(r).get("agent_id");
        assertNotNull(agentId);

        Response renamed = updateAgent(agentId, "{\"name\":\"it-agent-rename-dst-" + System.nanoTime() + "\"}");
        assertEquals(200, renamed.getStatusLine().getStatusCode());
    }

    public void testRename_ToExistingName_Rejected_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        // Register two distinct agents
        Response first = registerAgent(registerAgentBody("it-agent-rename-existing"));
        assertEquals(200, first.getStatusLine().getStatusCode());
        String firstId = (String) parseResponseToMap(first).get("agent_id");
        assertNotNull(firstId);

        Response second = registerAgent(registerAgentBody("it-agent-rename-loser"));
        String secondId = (String) parseResponseToMap(second).get("agent_id");
        assertNotNull(secondId);

        // Try to rename the second one onto the first one's name
        try {
            updateAgent(secondId, "{\"name\":\"it-agent-rename-existing\"}");
            fail("Expected rename to an existing name to be rejected with 409");
        } catch (ResponseException e) {
            assertEquals(409, e.getResponse().getStatusLine().getStatusCode());
            String payload = TestHelper.httpEntityToString(e.getResponse().getEntity());
            assertTrue(
                "409 response should cite the duplicate name, got: " + payload,
                payload.contains("already exists") && payload.contains("it-agent-rename-existing")
            );
            assertFalse("409 response must not leak the conflicting agent id, got: " + payload, payload.contains(firstId));
        }
    }

    public void testRename_SameName_NoOp_Accepted_WhenFlagOn() throws IOException {
        updateClusterSettings(SETTING_KEY, true);

        Response r = registerAgent(registerAgentBody("it-agent-rename-self"));
        String agentId = (String) parseResponseToMap(r).get("agent_id");
        assertNotNull(agentId);

        // PUT with the same name: must NOT 409 against itself.
        Response same = updateAgent(agentId, "{\"name\":\"it-agent-rename-self\"}");
        assertEquals(200, same.getStatusLine().getStatusCode());
    }

    public void testRename_ToExistingName_Allowed_WhenFlagOff() throws IOException {
        // Flag stays off (default from @Before). Rename onto an existing name must succeed (BWC).
        Response first = registerAgent(registerAgentBody("it-agent-rename-bwc"));
        assertEquals(200, first.getStatusLine().getStatusCode());

        Response second = registerAgent(registerAgentBody("it-agent-rename-bwc-other"));
        String secondId = (String) parseResponseToMap(second).get("agent_id");

        Response renamed = updateAgent(secondId, "{\"name\":\"it-agent-rename-bwc\"}");
        assertEquals(200, renamed.getStatusLine().getStatusCode());
    }

    private static Response registerAgent(String body) throws IOException {
        return TestHelper.makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(body), null);
    }

    private static Response updateAgent(String agentId, String body) throws IOException {
        return TestHelper.makeRequest(client(), "PUT", "/_plugins/_ml/agents/" + agentId, null, TestHelper.toHttpEntity(body), null);
    }

    private static String registerAgentBody(String name) {
        return "{\n"
            + "  \"name\": \""
            + name
            + "\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"description\": \"uniqueness IT agent\",\n"
            + "  \"tools\": []\n"
            + "}";
    }
}
