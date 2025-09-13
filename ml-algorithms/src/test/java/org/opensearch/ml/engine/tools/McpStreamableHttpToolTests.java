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

public class McpStreamableHttpToolTests {

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
        tool = McpStreamableHttpTool.Factory.getInstance().create(Map.of(MCP_SYNC_CLIENT, mcpSyncClient));
        validParams = Map.of("input", "{\"foo\":\"bar\"}");
    }

    @Test
    public void testRun_Success() {
        // Mock the MCP client response
        McpSchema.CallToolResult mockResult = new McpSchema.CallToolResult("test response", false);
        
        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenReturn(mockResult);

        // Execute the tool
        tool.run(validParams, listener);

        // Verify the client was called and listener was notified
        verify(mcpSyncClient).callTool(any(McpSchema.CallToolRequest.class));
        verify(listener).onResponse("[{\"text\":\"test response\"}]");
        verify(listener, never()).onFailure(any());
    }

    @Test
    public void testRun_ClientException() {
        // Mock the MCP client to throw an exception
        RuntimeException testException = new RuntimeException("MCP client error");
        when(mcpSyncClient.callTool(any(McpSchema.CallToolRequest.class))).thenThrow(testException);

        // Execute the tool
        tool.run(validParams, listener);

        // Verify the client was called and listener was notified of failure
        verify(mcpSyncClient).callTool(any(McpSchema.CallToolRequest.class));
        verify(listener).onFailure(testException);
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testRun_InvalidInput() {
        Map<String, String> invalidParams = Map.of("input", "invalid json");

        // Execute the tool with invalid input
        tool.run(invalidParams, listener);

        // Verify the listener was notified of failure
        verify(listener).onFailure(any());
        verify(listener, never()).onResponse(any());
    }

    @Test
    public void testValidate_ValidParams() {
        assertTrue(tool.validate(validParams));
    }

    @Test
    public void testValidate_EmptyParams() {
        assertFalse(tool.validate(Collections.emptyMap()));
    }

    @Test
    public void testValidate_NullParams() {
        assertFalse(tool.validate(null));
    }

    @Test
    public void testGetType() {
        assertEquals("McpStreamableHttpTool", tool.getType());
    }

    @Test
    public void testGetVersion() {
        assertNull(tool.getVersion());
    }

    @Test
    public void testGetName() {
        assertEquals("McpStreamableHttpTool", tool.getName());
    }

    @Test
    public void testSetName() {
        String newName = "CustomToolName";
        tool.setName(newName);
        assertEquals(newName, tool.getName());
    }

    @Test
    public void testGetDescription() {
        assertEquals("A tool from MCP Streamable HTTP Server", tool.getDescription());
    }

    @Test
    public void testSetDescription() {
        String newDescription = "Custom description";
        tool.setDescription(newDescription);
        assertEquals(newDescription, tool.getDescription());
    }

    @Test
    public void testFactory_Singleton() {
        McpStreamableHttpTool.Factory factory1 = McpStreamableHttpTool.Factory.getInstance();
        McpStreamableHttpTool.Factory factory2 = McpStreamableHttpTool.Factory.getInstance();
        assertEquals(factory1, factory2);
    }

    @Test
    public void testFactory_Create() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        Tool createdTool = factory.create(Map.of(MCP_SYNC_CLIENT, mcpSyncClient));
        
        assertTrue(createdTool instanceof McpStreamableHttpTool);
        assertEquals("McpStreamableHttpTool", createdTool.getType());
    }

    @Test
    public void testFactory_GetDefaultDescription() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        assertEquals("A tool from MCP Streamable HTTP Server", factory.getDefaultDescription());
    }

    @Test
    public void testFactory_GetDefaultType() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        assertEquals("McpStreamableHttpTool", factory.getDefaultType());
    }

    @Test
    public void testFactory_GetDefaultVersion() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        assertNull(factory.getDefaultVersion());
    }

    @Test
    public void testFactory_GetAllModelKeys() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        assertTrue(factory.getAllModelKeys().isEmpty());
    }

    @Test
    public void testFactory_Init() {
        McpStreamableHttpTool.Factory factory = McpStreamableHttpTool.Factory.getInstance();
        // Should not throw any exception
        factory.init();
    }
}