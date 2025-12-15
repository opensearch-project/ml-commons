/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.utils.TestHelper;

public class MLContextManagementRestIT extends MLCommonsRestTestCase {

    @Before
    public void setup() throws IOException {
        // Enable agent framework
        updateClusterSettings("plugins.ml_commons.agent_framework_enabled", true);
    }

    @Test
    public void testCreateAndUpdateContextManagementTemplate() throws IOException {
        String templateName = "test_template_" + System.currentTimeMillis();

        // Test create with POST
        String createRequestBody = "{\n"
            + "  \"name\": \""
            + templateName
            + "\",\n"
            + "  \"description\": \"Test template for integration test\",\n"
            + "  \"context_managers\": []\n"
            + "}";

        Response createResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/context_management/" + templateName,
                null,
                TestHelper.toHttpEntity(createRequestBody),
                null
            );
        assertEquals(201, createResponse.getStatusLine().getStatusCode());

        // Test update with PUT
        String updateRequestBody = "{\n"
            + "  \"name\": \""
            + templateName
            + "\",\n"
            + "  \"description\": \"Updated test template\",\n"
            + "  \"context_managers\": []\n"
            + "}";

        Response updateResponse = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "/_plugins/_ml/context_management/" + templateName,
                null,
                TestHelper.toHttpEntity(updateRequestBody),
                null
            );
        assertEquals(200, updateResponse.getStatusLine().getStatusCode());

        // Verify the template was updated
        Response getResponse = TestHelper.makeRequest(client(), "GET", "/_plugins/_ml/context_management/" + templateName, null, "", null);
        assertEquals(200, getResponse.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(getResponse);
        assertEquals("Updated test template", responseMap.get("description"));

        // Clean up
        TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/context_management/" + templateName, null, "", null);
    }
}
