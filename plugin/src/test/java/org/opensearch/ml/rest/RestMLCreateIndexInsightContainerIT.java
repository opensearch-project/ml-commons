/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.ParseException;
import org.junit.After;
import org.junit.Before;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;

public class RestMLCreateIndexInsightContainerIT extends RestBaseAgentToolsIT {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testCreateIndexInsightContainer_successful() throws IOException, ParseException {
        String registerAgentRequestBody = """
            {
                "index_name": "test_index"
            }
            """;
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_container",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(response);
        String responseBody = TestHelper.httpEntityToString(response.getEntity());
        Map<String, String> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("acknowledged"));
        assertEquals(true, body.get("acknowledged"));
        Response indexReponse = TestHelper
            .makeRequest(client(), "GET", "/test_index/", null, TestHelper.toHttpEntity(registerAgentRequestBody), null);
        assertNotNull(indexReponse);
    }

    public void testCreateIndexInsightContainer_failBecauseAlreadyHave() throws IOException, ParseException {
        String registerAgentRequestBody = """
            {
                "index_name": "test_index"
            }
            """;
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_container",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(response);
        ResponseException e = assertThrows(ResponseException.class, () -> {
            Response responseRepeat = TestHelper
                .makeRequest(
                    client(),
                    "PUT",
                    "/_plugins/_ml/index_insight_container",
                    null,
                    TestHelper.toHttpEntity(registerAgentRequestBody),
                    null
                );
        });
        assertNotNull(e);
        String responseBody = TestHelper.httpEntityToString(e.getResponse().getEntity());
        Map<String, Object> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("error"));
        assertEquals(500.0, body.get("status"));
        Map<String, String> error = (Map<String, String>) body.get("error");
        assertTrue(error.containsKey("reason"));
        assertEquals("Index insight container is already set. If you want to update, please delete it first.", error.get("reason"));
    }

    public void testCreateIndexInsightContainer_SuccessRePutAfterDelete() throws IOException, ParseException {
        String registerAgentRequestBody = """
            {
                "index_name": "test_index"
            }
            """;
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_container",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(response);
        ResponseException e = assertThrows(ResponseException.class, () -> {
            Response responseRepeat = TestHelper
                .makeRequest(
                    client(),
                    "PUT",
                    "/_plugins/_ml/index_insight_container",
                    null,
                    TestHelper.toHttpEntity(registerAgentRequestBody),
                    null
                );
        });
        assertNotNull(e);
        Response responseDelete = TestHelper
            .makeRequest(
                client(),
                "DELETE",
                "/_plugins/_ml/index_insight_container",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(responseDelete);
        Response responseReput = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/index_insight_container",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(response);
        String responseBody = TestHelper.httpEntityToString(responseReput.getEntity());
        Map<String, String> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("acknowledged"));
        assertEquals(true, body.get("acknowledged"));
        Response indexReponse = TestHelper
            .makeRequest(client(), "GET", "/test_index/", null, TestHelper.toHttpEntity(registerAgentRequestBody), null);
        assertNotNull(indexReponse);
    }

}
