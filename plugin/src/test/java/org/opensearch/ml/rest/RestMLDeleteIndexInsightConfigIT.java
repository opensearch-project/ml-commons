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

public class RestMLDeleteIndexInsightConfigIT extends RestBaseAgentToolsIT {
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testDeleteIndexInsightContainer_SuccessDelete() throws IOException, ParseException {
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
        TestHelper.makeRequest(client(), "GET", "/test_index/", null, TestHelper.toHttpEntity(registerAgentRequestBody), null);

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
        String responseBody = TestHelper.httpEntityToString(responseDelete.getEntity());
        Map<String, String> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("acknowledged"));
        assertEquals(true, body.get("acknowledged"));

        ResponseException responseException = assertThrows(
            ResponseException.class,
            () -> TestHelper.makeRequest(client(), "GET", "/test_index/", null, TestHelper.toHttpEntity(registerAgentRequestBody), null)
        );

        assertNotNull(responseException);
        Map<String, Object> errorBody = gson
            .fromJson(TestHelper.httpEntityToString(responseException.getResponse().getEntity()), Map.class);
        assertTrue(errorBody.containsKey("error"));
        Map<String, Object> error = (Map<String, Object>) errorBody.get("error");
        assertEquals("no such index [test_index]", error.get("reason"));
    }

    public void testDeleteIndexInsightContainer_FailSinceNotSet() throws IOException, ParseException {
        String registerAgentRequestBody = "";
        ResponseException responseException = assertThrows(
            ResponseException.class,
            () -> TestHelper
                .makeRequest(
                    client(),
                    "DELETE",
                    "/_plugins/_ml/index_insight_container",
                    null,
                    TestHelper.toHttpEntity(registerAgentRequestBody),
                    null
                )
        );

        assertNotNull(responseException);
        Map<String, Object> errorBody = gson
            .fromJson(TestHelper.httpEntityToString(responseException.getResponse().getEntity()), Map.class);
        assertTrue(errorBody.containsKey("error"));
        Map<String, Object> error = (Map<String, Object>) errorBody.get("error");
        assertEquals("Failed to get data object from index .plugins-ml-index-insight-container", error.get("reason"));
    }

}
