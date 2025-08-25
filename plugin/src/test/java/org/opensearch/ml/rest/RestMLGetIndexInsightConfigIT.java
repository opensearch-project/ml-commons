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
import org.opensearch.ml.utils.TestHelper;

public class RestMLGetIndexInsightConfigIT extends RestBaseAgentToolsIT {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testGetIndexInsightContainer_successful() throws IOException, ParseException {
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
            .makeRequest(client(), "GET", "/ml_index_insight/", null, TestHelper.toHttpEntity(putIndexInsightConfigBody), null);
        assertNotNull(indexReponse);

        String getIndexInsightConfig = """
            {
                "is_enable": true
            }
            """;
        Response responseGet = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/index_insight_config", null, TestHelper.toHttpEntity(getIndexInsightConfig), null);
        assertNotNull(responseGet);
        String responseGetBody = TestHelper.httpEntityToString(responseGet.getEntity());
        Map<String, Object> getBody = gson.fromJson(responseGetBody, Map.class);
        assertEquals(true, getBody.getOrDefault("is_enable", false));

    }

}
