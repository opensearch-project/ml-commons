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

    public void testGetIndexInsight_Success() throws IOException, ParseException, InterruptedException {
        enableSettings();
        String registerAgentRequestBody = """
            {
                "is_enable": true
            }
            """;
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
                "{\"example_docs\":[{\"field1\":200,\"field2\":\"text\"}],\"important_column_and_distribution\":{\"field2\":{\"type\":\"keyword\",\"unique_count\":1.0,\"unique_terms\":[\"text\"]},\"field1\":{\"min_value\":200.0,\"type\":\"long\",\"unique_count\":1.0,\"unique_terms\":[200.0],\"max_value\":200.0}}}",
                Map.class
            );
        assertEquals(gson.fromJson((String) indexInsight.get("content"), Map.class), targetContent);

    }

    public void testGetIndexInsightWithPattern_Success() throws IOException, ParseException, InterruptedException {
        enableSettings();
        String registerAgentRequestBody = """
            {
                "is_enable": true
            }
            """;
        createIndexAndDocWithPattern();

        Response responseIndexInsight = TestHelper
            .makeRequest(
                client(),
                "GET",
                "/_plugins/_ml/insights/target_index*/STATISTICAL_DATA",
                null,
                TestHelper.toHttpEntity(registerAgentRequestBody),
                null
            );
        assertNotNull(responseIndexInsight);
        String responseBody = TestHelper.httpEntityToString(responseIndexInsight.getEntity());
        Map<String, Object> body = gson.fromJson(responseBody, Map.class);
        assertTrue(body.containsKey("index_insight"));
        Map<String, Object> indexInsight = (Map<String, Object>) body.get("index_insight");
        assertEquals(indexInsight.get("index_name"), "target_index*");
        assertEquals(indexInsight.get("status"), "COMPLETED");
        assertEquals(indexInsight.get("task_type"), "STATISTICAL_DATA");
        Map<String, Object> targetContent = gson
            .fromJson(
                "{\"example_docs\":[{\"field1\":200},{\"field2\":\"text\"}],\"important_column_and_distribution\":{\"field2\":{\"type\":\"keyword\",\"unique_count\":1.0,\"unique_terms\":[\"text\"]},\"field1\":{\"min_value\":200.0,\"type\":\"long\",\"unique_count\":1.0,\"unique_terms\":[200.0],\"max_value\":200.0}}}",
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

    private void createIndexAndDocWithPattern() throws IOException, InterruptedException {
        String mapping = """
            {"mappings": {
                "properties": {
                    "field1": {
                        "type": "long"
                        }
                    }
                }
            }
            """;
        TestHelper.makeRequest(client(), "PUT", "/target_index1/", null, TestHelper.toHttpEntity(mapping), null);
        String mapping2 = """
            {"mappings": {
                "properties": {
                    "field2": {
                        "type": "keyword"
                        }
                    }
                }
            }
            """;
        TestHelper.makeRequest(client(), "PUT", "/target_index2/", null, TestHelper.toHttpEntity(mapping2), null);

        String putDoc = """
            {
                "field1": 200
            }
            """;
        TestHelper.makeRequest(client(), "POST", "/target_index1/_doc", null, TestHelper.toHttpEntity(putDoc), null);
        String putDoc2 = """
            {
                "field2": "text"
            }
            """;
        TestHelper.makeRequest(client(), "POST", "/target_index2/_doc", null, TestHelper.toHttpEntity(putDoc2), null);

        Thread.sleep(3000);
        Response response = TestHelper.makeRequest(client(), "GET", "/target_index*/_search", null, TestHelper.toHttpEntity("{}"), null);
        assertNotNull(response);
    }

    public static void enableSettings() throws IOException {
        String enableSettingBody = """
                {
                   "persistent" : {
                     "plugins.ml_commons.index_insight_feature_enabled" : true
                   }
                 }
            """;
        Response response = TestHelper
            .makeRequest(client(), "PUT", "/_cluster/settings/", null, TestHelper.toHttpEntity(enableSettingBody), null);
        assertNotNull(response);
    }

}
