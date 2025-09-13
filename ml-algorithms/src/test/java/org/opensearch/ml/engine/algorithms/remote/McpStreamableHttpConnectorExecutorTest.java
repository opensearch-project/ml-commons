/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;
import org.opensearch.ml.engine.MLStaticMockBase;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

public class McpStreamableHttpConnectorExecutorTest extends MLStaticMockBase {

    @Mock
    private McpStreamableHttpConnector mockConnector;
    @Mock
    private McpSyncClient mcpClient;
    @Mock
    private McpClient.SyncSpec builder;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        Map<String, String> decryptedHeaders = Map.of("Authorization", "Bearer secret-token");

        when(mockConnector.getUrl()).thenReturn("http://random-url");
        when(mockConnector.getDecryptedHeaders()).thenReturn(decryptedHeaders);

        /* ---------- stub the fluent builder chain ------------------------ */
        when(builder.requestTimeout(any())).thenReturn(builder);
        when(builder.capabilities(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mcpClient);
    }

    @Test
    public void testGetMcpToolSpecs_Success() throws Exception {
        // Mock the MCP client and schema
        McpSchema.ListToolsResult mockResult = createMockListToolsResult();
        when(mcpClient.listTools()).thenReturn(mockResult);

        // Mock the transport builder
        try (
            MockedStatic<io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport> transportMock = mockStatic(
                io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.class
            )
        ) {

            io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder transportBuilder = org.mockito.Mockito
                .mock(io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder.class);
            io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport transport = org.mockito.Mockito
                .mock(io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.class);

            transportMock
                .when(() -> io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(any()))
                .thenReturn(transportBuilder);
            when(transportBuilder.endpoint(any())).thenReturn(transportBuilder);
            when(transportBuilder.customizeClient(any())).thenReturn(transportBuilder);
            when(transportBuilder.customizeRequest(any())).thenReturn(transportBuilder);
            when(transportBuilder.build()).thenReturn(transport);

            // Mock McpClient.sync
            try (MockedStatic<McpClient> clientMock = mockStatic(McpClient.class)) {
                clientMock.when(() -> McpClient.sync(transport)).thenReturn(builder);

                McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);
                List<MLToolSpec> result = exec.getMcpToolSpecs();

                Assert.assertNotNull(result);
                Assert.assertEquals(1, result.size());
                Assert.assertEquals("test_tool", result.get(0).getName());
                Assert.assertEquals("McpStreamableHttpTool", result.get(0).getType());
            }
        }
    }

    @Test
    public void testGetMcpToolSpecs_Exception() throws Exception {
        when(mcpClient.listTools()).thenThrow(new RuntimeException("MCP server error"));

        // Mock the transport builder
        try (MockedStatic<io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport> transportMock = 
             mockStatic(io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.class)) {
            
            io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder transportBuilder = 
                org.mockito.Mockito.mock(io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder.class);
            io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport transport = 
                org.mockito.Mockito.mock(io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.class);
            
            transportMock.when(() -> io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.builder(any()))
                .thenReturn(transportBuilder);
            when(transportBuilder.endpoint(any())).thenReturn(transportBuilder);
            when(transportBuilder.customizeClient(any())).thenReturn(transportBuilder);
            when(transportBuilder.customizeRequest(any())).thenReturn(transportBuilder);
            when(transportBuilder.build()).thenReturn(transport);

            // Mock McpClient.sync
            try (MockedStatic<McpClient> clientMock = mockStatic(McpClient.class)) {
                clientMock.when(() -> McpClient.sync(transport)).thenReturn(builder);

                McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);
                
                Exception exception = assertThrows(Exception.class, () -> exec.getMcpToolSpecs());
                Assert.assertTrue(exception.getMessage().contains("Unexpected error while getting MCP tools"));
            }
        }
    }

    @Test
    public void testGetMcpToolSpecs_NullUrl() {
        when(mockConnector.getUrl()).thenReturn(null);

        McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);
        List<MLToolSpec> result = exec.getMcpToolSpecs();

        Assert.assertNotNull(result);
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testUnsupportedOperations() {
        McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);

        assertThrows(UnsupportedOperationException.class, () -> exec.getScriptService());
        assertThrows(UnsupportedOperationException.class, () -> exec.getClient());
        assertThrows(UnsupportedOperationException.class, () -> exec.invokeRemoteService("test", null, null, null, null, null));
    }

    @Test
    public void testGetLogger() {
        McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);
        Assert.assertNotNull(exec.getLogger());
    }

    private McpSchema.ListToolsResult createMockListToolsResult() {
        McpSchema.Tool tool = McpSchema.Tool.builder().name("test_tool").description("A test tool").build();

        return new McpSchema.ListToolsResult(List.of(tool), null);
    }
}
