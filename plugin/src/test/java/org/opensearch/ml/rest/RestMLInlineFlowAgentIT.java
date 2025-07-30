/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMLInlineFlowAgentIT extends MLCommonsRestTestCase {

    private final String TEST_INDEX_NAME = "test_index";
    private final String TEST_INDEX_NAME2 = "test_index2";

    private static final String INLINE_AGENT_TEMPLATE = """
        {
            "name": "test agent",
            "type": "flow",
            "description": "Inline flow agent with list index tool",
            "tools": [
                {
                    "type": "ListIndexTool",
                    "name": "list_indices",
                    "description": "Tool to list all indices",
                    "parameters": {}
                }
            ]
        }""";

    @Before
    public void setup() throws IOException, InterruptedException {
        createIndex(TEST_INDEX_NAME, Settings.EMPTY);
        createIndex(TEST_INDEX_NAME2, Settings.EMPTY);

        List<String> dataList = new ArrayList<>();
        dataList.add("{\"name\":\"John Doe\",\"age\":30,\"city\":\"New York\",\"description\":\"Software Engineer\"}");
        dataList.add("{\"name\":\"Jane Smith\",\"age\":25,\"city\":\"Los Angeles\",\"description\":\"Data Scientist\"}");
        dataList.add("{\"name\":\"Bob Johnson\",\"age\":35,\"city\":\"Chicago\",\"description\":\"DevOps Engineer\"}");
        dataList.add("{\"name\":\"Alice Brown\",\"age\":28,\"city\":\"Seattle\",\"description\":\"Product Manager\"}");

        for (String data : dataList) {
            ingestData(TEST_INDEX_NAME, data);
        }

        Thread.sleep(1000);
    }

    @After
    public void deleteIndex() throws IOException {
        deleteIndex(TEST_INDEX_NAME);
    }

    @Test
    public void testInlineFlowAgentWithListIndexTool() throws IOException, InterruptedException {
        // Test inline flow agent with listIndexTool
        String requestBody = """
            {
            "agent": %s,
            "parameters": {}
            }""".formatted(INLINE_AGENT_TEMPLATE);

        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_execute", null, TestHelper.toHttpEntity(requestBody), List.of());

        assertEquals(RestStatus.OK.getStatus(), response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        validateResponseStructure(responseMap);

        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertTrue(inferenceResults.size() > 0);

        Map<String, Object> result = inferenceResults.get(0);
        List<Map<String, Object>> output = (List<Map<String, Object>>) result.get("output");
        assertNotNull(output);
        assertTrue(output.size() > 0);

        Map<String, Object> outputData = output.get(0);
        assertNotNull(outputData.get("result"));

        String resultString = (String) outputData.get("result");
        assertTrue(resultString.contains(TEST_INDEX_NAME));
        assertTrue(resultString.contains(TEST_INDEX_NAME2));
    }

    @Test
    public void testInlineFlowAgentWithListIndexToolAndUserInput() throws IOException, InterruptedException {
        // Test inline flow agent with listIndexTool and user input parameters
        String requestBody = """
            {
            "agent": %s,
            "parameters" : {
               "index": "test_index"
            }
            }""".formatted(INLINE_AGENT_TEMPLATE);

        Response response = TestHelper
            .makeRequest(client(), "POST", "/_plugins/_ml/agents/_execute", null, TestHelper.toHttpEntity(requestBody), List.of());

        assertEquals(RestStatus.OK.getStatus(), response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        validateResponseStructure(responseMap);

        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertTrue(inferenceResults.size() > 0);

        Map<String, Object> result = inferenceResults.get(0);
        List<Map<String, Object>> output = (List<Map<String, Object>>) result.get("output");
        assertNotNull(output);
        assertTrue(output.size() > 0);

        Map<String, Object> outputData = output.get(0);
        assertNotNull(outputData.get("result"));

        String resultString = (String) outputData.get("result");
        assertTrue(resultString.contains("test_index"));
    }

    @Test
    public void testInlineFlowAgentWithMultipleTools() throws IOException, InterruptedException {
        // Test inline flow agent with multiple tools including listIndexTool
        String inlineAgent = """
            {
                "name": "agent_with_multi_tools",
                "type": "flow",
                "description": "Inline flow agent with multiple tools",
                "tools": [
                    {
                        "type": "ListIndexTool",
                        "name": "list_indices",
                        "description": "Tool to list all indices",
                        "parameters": {}
                    },
                    {
                        "type": "SearchIndexTool",
                        "name": "search_test_index",
                        "description": "Tool to search test index",
                        "parameters": {
                            "index": "%s",
                            "query": {
                                "query": {
                                    "match_all": {}
                                }
                            }
                        }
                    }
                ]
            }""".formatted(TEST_INDEX_NAME);

        String requestBody = """
            {
                "agent": %s,
                "parameters": {}
            }""".formatted(inlineAgent);

        Response response = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/agents/_execute",
                null,
                TestHelper.toHttpEntity(requestBody),
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, "Kibana"))
            );

        assertEquals(RestStatus.OK.getStatus(), response.getStatusLine().getStatusCode());

        Map<String, Object> responseMap = parseResponseToMap(response);
        validateResponseStructure(responseMap);

        List<Map<String, Object>> inferenceResults = (List<Map<String, Object>>) responseMap.get("inference_results");
        assertNotNull(inferenceResults);
        assertTrue(inferenceResults.size() > 0);
        System.out.println(gson.toJson(inferenceResults));
        // search index tool has been executed
        assertEquals("search_test_index", ((List<Map<String, Object>>) inferenceResults.getFirst().get("output")).getFirst().get("name"));
    }

    private void validateResponseStructure(Map<String, Object> responseMap) {
        assertNotNull(responseMap);
        assertTrue(responseMap.containsKey("inference_results"));
    }

    private void ingestData(String indexName, String data) throws IOException {
        Response response = TestHelper
            .makeRequest(client(), "POST", "/" + indexName + "/_doc", null, TestHelper.toHttpEntity(data), List.of());
        assertEquals(RestStatus.CREATED.getStatus(), response.getStatusLine().getStatusCode());
    }
}
