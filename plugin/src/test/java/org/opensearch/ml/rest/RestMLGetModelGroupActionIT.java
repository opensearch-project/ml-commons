/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.apache.hc.core5.http.HttpEntity;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

public class RestMLGetModelGroupActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    public void testGetModelAPI_EmptyResources() throws IOException {
        exceptionRule.expect(ResponseException.class);
        exceptionRule.expectMessage("Fail to find model group index");
        TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/model_groups/111222333", null, "", null);
    }

    @Ignore
    public void testGetModelAPI_Success() throws IOException {
        Response trainModelResponse = ingestModelData();
        HttpEntity entity = trainModelResponse.getEntity();
        assertNotNull(trainModelResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String model_group_id = (String) map.get("model_group_id");

        Response getModelResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/" + model_group_id, null, "", null);
        assertNotNull(getModelResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(getModelResponse));
    }
}
