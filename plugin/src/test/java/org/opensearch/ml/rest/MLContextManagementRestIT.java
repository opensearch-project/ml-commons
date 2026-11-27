/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            + "  \"hooks\": {\n"
            + "    \"pre_execution\": [\n"
            + "      {\n"
            + "        \"type\": \"TestContextManager\"\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
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
        assertEquals(200, createResponse.getStatusLine().getStatusCode());

        // Test update with PUT
        String updateRequestBody = "{\n"
            + "  \"name\": \""
            + templateName
            + "\",\n"
            + "  \"description\": \"Updated test template\",\n"
            + "  \"hooks\": {\n"
            + "    \"pre_execution\": [\n"
            + "      {\n"
            + "        \"type\": \"TestContextManager\"\n"
            + "      }\n"
            + "    ]\n"
            + "  }\n"
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

    @Test
    public void testUpdateReplacesHooks() throws IOException {
        String templateName = "test_replace_hooks_" + System.currentTimeMillis();
        String createRequestBody = "{\n"
            + "  \"description\": \"Two hooks\",\n"
            + "  \"hooks\": {\n"
            + "    \"PRE_LLM\": [ { \"type\": \"SlidingWindowManager\", \"config\": { \"max_messages\": 6 } } ],\n"
            + "    \"POST_TOOL\": [ { \"type\": \"ToolsOutputTruncateManager\", \"config\": { \"max_output_length\": 100 } } ]\n"
            + "  }\n"
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
        assertEquals(200, createResponse.getStatusLine().getStatusCode());

        try {
            // Drop the PRE_LLM hook and change the POST_TOOL config.
            String updateRequestBody = "{\n"
                + "  \"hooks\": {\n"
                + "    \"POST_TOOL\": [ { \"type\": \"ToolsOutputTruncateManager\", \"config\": { \"max_output_length\": 200 } } ]\n"
                + "  }\n"
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

            Response getResponse = TestHelper
                .makeRequest(client(), "GET", "/_plugins/_ml/context_management/" + templateName, null, "", null);
            Map<String, Object> responseMap = parseResponseToMap(getResponse);
            Map<String, Object> hooks = (Map<String, Object>) responseMap.get("hooks");
            assertEquals(Set.of("POST_TOOL"), hooks.keySet());
            List<Map<String, Object>> postTool = (List<Map<String, Object>>) hooks.get("POST_TOOL");
            assertEquals(200, ((Number) ((Map<String, Object>) postTool.get(0).get("config")).get("max_output_length")).intValue());
            // Fields the update did not set are kept.
            assertEquals("Two hooks", responseMap.get("description"));
            assertNotNull(responseMap.get("created_time"));
        } finally {
            TestHelper.makeRequest(client(), "DELETE", "/_plugins/_ml/context_management/" + templateName, null, "", null);
        }
    }
}
