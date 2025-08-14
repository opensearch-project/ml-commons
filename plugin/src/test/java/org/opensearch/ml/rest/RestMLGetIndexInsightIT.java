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

public class RestMLGetIndexInsightIT extends RestBaseAgentToolsIT {
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        deleteExternalIndices();
    }

    public void testDeleteIndexInsightContainer_SuccessDelete() throws IOException, ParseException, InterruptedException {
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
        createIndexAndPutDoc();

        Response responseIndexInsight = TestHelper
            .makeRequest(
                client(),
                "GET",
                "/_plugins/_ml/insights/target_index/STATISTICAL_DATA",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(responseIndexInsight);
        String responseBody = TestHelper.httpEntityToString(responseIndexInsight.getEntity());
        Map<String, Object> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("index_insight"));
        Map<String, Object> indexInsight = (Map<String, Object>) body.get("index_insight");
        assertEquals(indexInsight.get("index_name"), "target_index");
        assertEquals(indexInsight.get("status"), "COMPLETED");
        assertEquals(indexInsight.get("task_type"), "STATISTICAL_DATA");
        Map<String, Object> targetContent = gson
            .fromJson(
                "{\"mapping\":{\"field1\":{\"type\":\"long\"},\"field2\":{\"type\":\"keyword\"}},\"distribution\":{\"field1\":{\"min_value\":200.0,\"unique_count\":1.0,\"unique_terms\":[200.0],\"max_value\":200.0},\"example_docs\":[{\"field1\":200,\"field2\":\"text\"}],\"field2\":{\"unique_count\":1.0,\"unique_terms\":[\"text\"]}}}",
                Map.class
            );
        assertEquals(gson.fromJson((String) indexInsight.get("content"), Map.class), targetContent);

    }

    private void createIndexAndPutDoc() throws IOException, InterruptedException {
        String mapping = """
            {"mappings": {
                "properties": {
                    "field1": {
                        "type": "long"
                        },
                    "field2": {
                        "type": "keyword"
                        }
                    }
                }
            }
            """;
        TestHelper.makeRequest(client(), "PUT", "/target_index/", null, TestHelper.toHttpEntity(mapping), null);
        String putDoc = """
            {
                "field1": 200,
                "field2": "text"
            }
            """;
        TestHelper.makeRequest(client(), "POST", "/target_index/_doc", null, TestHelper.toHttpEntity(putDoc), null);
        Thread.sleep(3000);
        Response response = TestHelper.makeRequest(client(), "GET", "/target_index/_search", null, TestHelper.toHttpEntity("{}"), null);
        assertNotNull(response);
    }

}
