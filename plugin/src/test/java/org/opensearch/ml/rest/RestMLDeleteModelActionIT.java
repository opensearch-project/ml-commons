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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

public class RestMLDeleteModelActionIT extends MLCommonsRestTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Ignore
    public void testDeleteModelAPI_Success() throws IOException {
        Response trainModelResponse = ingestModelData();
        HttpEntity entity = trainModelResponse.getEntity();
        assertNotNull(trainModelResponse);
        String entityString = TestHelper.httpEntityToString(entity);
        Map map = gson.fromJson(entityString, Map.class);
        String model_id = (String) map.get("model_id");

        Response deleteModelResponse = TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/models/" + model_id, null, "", null);
        assertNotNull(deleteModelResponse);
        assertEquals(RestStatus.OK, TestHelper.restStatus(deleteModelResponse));
    }
}
