/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.utils.TestData.matchAllSearchQuery;

import java.io.IOException;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.opensearch.client.Response;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

public class RestMLSearchModelActionIT extends MLCommonsRestTestCase {

    public void testSearchModelAPI_EmptyResources() throws Exception {
        Response response = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/models/_search", null, matchAllSearchQuery(), null);
        Map responseMap = parseResponseToMap(response);
        assertEquals((Double) 0.0, (Double) ((Map) ((Map) responseMap.get("hits")).get("total")).get("value"));
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
