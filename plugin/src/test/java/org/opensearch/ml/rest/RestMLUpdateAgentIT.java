/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

public class RestMLUpdateAgentIT extends MLCommonsRestTestCase {

    public void testUpdateAgentReplacesParameters() throws IOException {
        String registerBody = "{\n"
            + "  \"name\": \"update_parameters_agent\",\n"
            + "  \"type\": \"flow\",\n"
            + "  \"parameters\": { \"keep_me\": \"1\", \"remove_me\": \"2\" },\n"
            + "  \"tools\": [ { \"type\": \"ListIndexTool\" } ]\n"
            + "}";
        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_register", null, TestHelper.toHttpEntity(registerBody), null);
        String agentId = (String) parseResponseToMap(response).get("agent_id");
        assertNotNull(agentId);

        try {
            String updateBody = "{ \"parameters\": { \"keep_me\": \"3\" } }";
            response = TestHelper
                .makeRequest(client(), "PUT", "/_plugins/_ml/agents/" + agentId, null, TestHelper.toHttpEntity(updateBody), null);
            assertEquals(200, response.getStatusLine().getStatusCode());

            response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/agents/" + agentId, null, "", null);
            Map<String, Object> agent = parseResponseToMap(response);
            assertEquals(Map.of("keep_me", "3"), agent.get("parameters"));
            assertEquals("update_parameters_agent", agent.get("name"));
        } finally {
            TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/agents/" + agentId, null, "", null);
        }
    }
}
