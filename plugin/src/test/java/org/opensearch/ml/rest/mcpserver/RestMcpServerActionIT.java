/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest.mcpserver;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.message.BasicHeader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.client.Response;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.rest.MLCommonsRestTestCase;
import org.opensearch.ml.utils.TestHelper;

import com.google.common.collect.ImmutableList;

public class RestMcpServerActionIT extends MLCommonsRestTestCase {

    @Before
    public void setup() throws IOException {
        // Enable MCP server
        Response response = TestHelper
            .makeRequest(
                client(),
                "PUT",
                "_cluster/settings",
                null,
                "{\"persistent\":{\"" + MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey() + "\":true}}",
                ImmutableList.of(new BasicHeader(HttpHeaders.USER_AGENT, ""))
            );
        assertEquals(200, response.getStatusLine().getStatusCode());

        // Register ListIndexTool
        String toolRegistrationBody = """
            {
                "tools": [
                    {
                        "name": "ListIndexTool",
                        "type": "ListIndexTool"
                       }
                ]
            }
            """;

        Response registerResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp/tools/_register",
                null,
                toolRegistrationBody,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
        assertEquals(200, registerResponse.getStatusLine().getStatusCode());
    }

    @After
    public void cleanup() throws IOException {
        // Clean up registered tools
        String removeToolBody = """
             ["ListIndexTool"]
            """;
        TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp/tools/_remove",
                null,
                removeToolBody,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );
    }

    @Test
    public void testMcpStatelessStreamingFlow() throws IOException {
        // Test case makes 4 API calls to http://localhost:9200/_plugins/_ml/mcp/stream

        // 1. Initialize Connection
        // Method: initialize
        // Purpose: Establishes the MCP protocol connection
        String initializeRequest = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {
                        "roots": {
                            "listChanged": true
                        },
                        "sampling": {}
                    },
                    "clientInfo": {
                        "name": "test-client",
                        "version": "1.0.0"
                    }
                }
            }
            """;

        Response initializeResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp",
                null,
                initializeRequest,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        assertEquals(200, initializeResponse.getStatusLine().getStatusCode());
        String initializeResponseBody = TestHelper.httpEntityToString(initializeResponse.getEntity());
        assertTrue(initializeResponseBody.contains("jsonrpc"));
        assertTrue(initializeResponseBody.contains("2.0"));
        assertTrue(initializeResponseBody.contains("id"));
        assertTrue(initializeResponseBody.contains("1"));

        // 2. Initialization Complete Notification
        // Method: notifications/initialized
        // Purpose: Notifies server that client initialization is complete
        String initializedNotification = """
            {
                "jsonrpc": "2.0",
                "method": "notifications/initialized",
                "params": {}
            }
            """;

        Response initializedResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp",
                null,
                initializedNotification,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        assertEquals(202, initializedResponse.getStatusLine().getStatusCode());

        // 3. List Available Tools
        // Method: tools/list
        // Purpose: Retrieves list of available tools from the server
        String toolsListRequest = """
            {
                "jsonrpc": "2.0",
                "id": 2,
                "method": "tools/list",
                "params": {}
            }
            """;

        Response toolsListResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp",
                null,
                toolsListRequest,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        assertEquals(200, toolsListResponse.getStatusLine().getStatusCode());
        String toolsListResponseBody = TestHelper.httpEntityToString(toolsListResponse.getEntity());
        assertTrue(toolsListResponseBody.contains("jsonrpc"));
        assertTrue(toolsListResponseBody.contains("2.0"));
        assertTrue(toolsListResponseBody.contains("id"));
        assertTrue(toolsListResponseBody.contains("2"));
        assertTrue(toolsListResponseBody.contains("ListIndexTool"));

        // 4. Call a Tool
        // Method: tools/call
        // Purpose: Executes the ListIndexTool with no arguments
        String toolCallRequest = """
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "ListIndexTool",
                    "arguments": {
                        "indices": []
                    }
                }
            }
            """;

        Response toolCallResponse = TestHelper
            .makeRequest(
                client(),
                "POST",
                "/_plugins/_ml/mcp",
                null,
                toolCallRequest,
                ImmutableList.of(new BasicHeader(HttpHeaders.CONTENT_TYPE, "application/json"))
            );

        assertEquals(200, toolCallResponse.getStatusLine().getStatusCode());
        String toolCallResponseBody = TestHelper.httpEntityToString(toolCallResponse.getEntity());

        // Parse response to verify structure
        @SuppressWarnings("unchecked")
        Map<String, Object> responseMap = gson.fromJson(toolCallResponseBody, Map.class);

        // Verify JSON-RPC structure
        assertEquals("2.0", responseMap.get("jsonrpc"));
        assertEquals(3, ((Number) responseMap.get("id")).intValue());
        assertNotNull(responseMap.get("result"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) responseMap.get("result");

        // Verify result structure
        assertNotNull(result.get("content"));
        assertNotNull(result.get("isError"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) result.get("content");
        assertEquals(1, content.size());

        // Verify content structure
        Map<String, Object> firstContent = content.get(0);
        assertEquals("text", firstContent.get("type"));
        assertNotNull(firstContent.get("text"));
        assertTrue(((String) firstContent.get("text")).length() > 0);

        // Verify isError is false
        Boolean isError = (Boolean) result.get("isError");
        assertFalse(isError);
    }
}
