/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.rest.RestMLGetIndexInsightIT.enableSettings;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

public class RestMLPutIndexInsightConfigIT extends RestBaseAgentToolsIT {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testPutIndexInsightContainer_successful() throws IOException, ParseException {
        enableSettings();
        String putIndexInsightConfigBody = """
            {
                "is_enable": true
            }
            """;
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_config",
                null,
                TestHelper.toHttpEntity(putIndexInsightConfigBody),
                null
            );
        assertNotNull(response);
        String responseBody = TestHelper.httpEntityToString(response.getEntity());
        Map<String, String> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("acknowledged"));
        assertEquals(true, body.get("acknowledged"));
        Response indexReponse = TestHelper
            .makeRequest(
                client(),
                "GET",
                "/.plugins-ml-index-insight-storage/",
                null,
                TestHelper.toHttpEntity(putIndexInsightConfigBody),
                null
            );
        assertNotNull(indexReponse);
    }

    public void testPutIndexInsightConfig_withAgentId() throws IOException, ParseException {
        enableSettings();
        String agentId = createAgent("""
            {
                "name": "index insight agent",
                "type": "flow",
                "tools": [{"type": "ListIndexTool"}]
            }
            """);
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_config",
                null,
                TestHelper.toHttpEntity("{\"is_enable\": true, \"agent_id\": \"" + agentId + "\"}"),
                null
            );
        Map<String, Object> body = gson.fromJson(TestHelper.httpEntityToString(response.getEntity()), Map.class);
        assertEquals(true, body.get("acknowledged"));

        Response getResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/index_insight_config", null, "", null);
        Map<String, Object> getBody = gson.fromJson(TestHelper.httpEntityToString(getResponse.getEntity()), Map.class);
        assertEquals(true, getBody.get("is_enable"));
        assertEquals(agentId, getBody.get("agent_id"));
    }

    public void testPutIndexInsightConfig_withNonexistentAgentId() throws IOException {
        enableSettings();
        ResponseException exception = expectThrows(
            ResponseException.class,
            () -> TestHelper
                .makeRequest(
                    client(),
                    "PUT",
                    "/_plugins/_ml/index_insight_config",
                    null,
                    TestHelper.toHttpEntity("{\"is_enable\": true, \"agent_id\": \"nonexistent-agent\"}"),
                    null
                )
        );
        assertEquals(404, exception.getResponse().getStatusLine().getStatusCode());
    }

    public void testPutIndexInsightConfig_withUnknownField() throws IOException {
        enableSettings();
        ResponseException exception = expectThrows(
            ResponseException.class,
            () -> TestHelper
                .makeRequest(
                    client(),
                    "PUT",
                    "/_plugins/_ml/index_insight_config",
                    null,
                    TestHelper.toHttpEntity("{\"is_enable\": true, \"agentId\": \"agent-1\"}"),
                    null
                )
        );
        assertEquals(400, exception.getResponse().getStatusLine().getStatusCode());
        assertTrue(exception.getMessage().contains("Unknown field [agentId] in index insight config"));
    }

}
