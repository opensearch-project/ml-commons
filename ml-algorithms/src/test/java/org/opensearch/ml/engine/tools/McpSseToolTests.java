/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MCP_SYNC_CLIENT;

import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.spi.tools.Tool;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

public class McpSseToolTests {

    @Mock
    private McpSyncClient mcpSyncClient;

    @Mock
    private ActionListener<String> listener;

    private Tool tool;
    private Map<String, String> validParams;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Initialize the tool with the mocked mcp client
        tool = McpSseTool.Factory.getInstance().create(Map.of(MCP_SYNC_CLIENT, mcpSyncClient));
        validParams = Map.of("input", "{\"foo\":\"bar\"}");
    }

    @Test
    public void testRunSuccess() {
        // create a CallToolResult wrapping a JSON string
        McpSchema.CallToolResult result = new McpSchema.CallToolResult("{\"foo\":\"bar\"}", false);
        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(result);

        tool.run(validParams, listener);

        // Assert
        verify(listener).onResponse("[{\"annotations\":null,\"text\":\"{\\\"foo\\\":\\\"bar\\\"}\",\"meta\":null}]");
        verify(listener, never()).onFailure(any());
    }

    @Test
    public void testRunInvalidJsonInput() {
        // Passing a non-JSON string should trigger failure in parsing
        Map<String, String> badParams = Map.of("input", "not-json");
        tool.run(badParams, listener);

        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testRunClientThrows() {
        // Simulate the MCP client throwing an exception
        when(mcpSyncClient.callTool(any())).thenThrow(new RuntimeException("client error"));

        tool.run(validParams, listener);

        verify(listener).onFailure(any(RuntimeException.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testRunMissingInputParam() {
        // No "input" key in parameters should be caught
        tool.run(Collections.emptyMap(), listener);

        verify(listener).onFailure(any(Exception.class));
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testValidateAndMetadata() {
        // validate
        assertTrue(tool.validate(validParams));
        assertFalse(tool.validate(Collections.emptyMap()));
        // metadata
        assertEquals(McpSseTool.TYPE, tool.getName());
        assertEquals(McpSseTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertEquals(McpSseTool.DEFAULT_DESCRIPTION, tool.getDescription());
    }

    @Test
    public void testFactoryDefaults() {
        McpSseTool.Factory factory = McpSseTool.Factory.getInstance();
        assertEquals(McpSseTool.DEFAULT_DESCRIPTION, factory.getDefaultDescription());
        assertEquals(McpSseTool.TYPE, factory.getDefaultType());
        assertNull(factory.getDefaultVersion());
        assertTrue(factory.getAllModelKeys().isEmpty());
    }
}
