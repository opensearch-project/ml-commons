/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.utils.TestData.matchAllSearchQuery;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

public class RestMLSearchModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testSearchModelAPI_EmptyResources() throws Exception {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("index_not_found_exception");
        TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, matchAllSearchQuery(), null);
    }

    public void testSearchModelAPI_Success() throws IOException {
        Response trainModelResponse = ingestModelData();
        HttpEntity entity = trainModelResponse.getEntity();
        assertNotNull(trainModelResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String model_id = (String) map.get("model_id");

        Response searchModelResponse = TestHelper
            .makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, matchAllSearchQuery(), null);
        assertNotNull(searchModelResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(searchModelResponse));
    }
}
