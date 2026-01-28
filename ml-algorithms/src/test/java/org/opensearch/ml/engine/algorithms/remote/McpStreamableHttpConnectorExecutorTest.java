/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import io.modelcontextprotocol.spec.McpClientTransport;
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
    public void getMcpToolSpecs_returnsExpectedSpecs() {

        String inputSchemaJSON =
            "{\"type\":\"object\",\"properties\":{\"state\":{\"title\":\"State\",\"type\":\"string\"}},\"required\":[\"state\"],\"additionalProperties\":false}";

        McpSchema.Tool tool = new McpSchema.Tool("tool1", "desc1", inputSchemaJSON);
        McpSchema.ListToolsResult mockTools = new McpSchema.ListToolsResult(List.of(tool), null);

        when(mcpClient.listTools()).thenReturn(mockTools);
        when(mcpClient.initialize()).thenReturn(null);

        try (MockedStatic<McpClient> mocked = mockStatic(McpClient.class)) {
            mocked.when(() -> McpClient.sync(any(McpClientTransport.class))).thenReturn(builder);
            McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);
            List<MLToolSpec> specs = exec.getMcpToolSpecs();

            Assert.assertEquals(1, specs.size());
            MLToolSpec spec = specs.get(0);
            Assert.assertEquals("tool1", spec.getName());
            Assert.assertEquals("desc1", spec.getDescription());
            Assert.assertEquals(inputSchemaJSON, spec.getAttributes().get("input_schema"));
            Assert.assertSame(mcpClient, spec.getRuntimeResources().get("mcp_sync_client"));
            mocked.verify(() -> McpClient.sync(any(McpClientTransport.class)));
            verify(builder, times(1)).build();
            verify(mcpClient, times(1)).initialize();
            verify(mcpClient, times(1)).listTools();
        }
    }

    @Test
    public void getMcpToolSpecs_throwsOnInitError() {

        when(mcpClient.initialize()).thenThrow(new RuntimeException("Error initializing"));
        try (MockedStatic<McpClient> mocked = mockStatic(McpClient.class)) {
            mocked.when(() -> McpClient.sync(any(McpClientTransport.class))).thenReturn(builder);
            McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);

            assertThrows(RuntimeException.class, () -> exec.getMcpToolSpecs());
        }
    }

    @Test
    public void getMcpToolSpecs_throwsOnListToolsError() {

        when(mcpClient.initialize()).thenReturn(null);
        when(mcpClient.listTools()).thenThrow(new RuntimeException("Error listing tools"));
        try (MockedStatic<McpClient> mocked = mockStatic(McpClient.class)) {
            mocked.when(() -> McpClient.sync(any(McpClientTransport.class))).thenReturn(builder);
            McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);

            assertThrows(RuntimeException.class, () -> exec.getMcpToolSpecs());
        }
    }

    @Test
    public void testUnimplementedMethods_ThrowUnsupportedOperationException() {
        McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);

        assertThrows(UnsupportedOperationException.class, () -> exec.invokeRemoteService(null, null, null, null, null, null));
        assertThrows(UnsupportedOperationException.class, () -> exec.getScriptService());
        assertThrows(UnsupportedOperationException.class, () -> exec.getRateLimiter());
        assertThrows(UnsupportedOperationException.class, () -> exec.getMlGuard());
        assertThrows(UnsupportedOperationException.class, () -> exec.getUserRateLimiterMap());

    }

}
