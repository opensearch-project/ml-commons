/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.rest.RestStatus;

public class RestMLDeleteModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testDeleteModelAPI_EmptyResources() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("index_not_found_exception");
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/models/111222333", null, "", null);
    }

    public void testDeleteModelAPI_Success() throws IOException {
        Response trainModelResponse = ingestModelData();
        HttpEntity entity = trainModelResponse.getEntity();
        assertNotNull(trainModelResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String model_id = (String) map.get("model_id");

        Response getModelResponse = TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/models/" + model_id, null, "", null);
        assertNotNull(getModelResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(getModelResponse));
    }
}
