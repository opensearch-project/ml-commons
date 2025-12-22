package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.common.CommonValue.*;

import java.net.http.HttpRequest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.common.connector.AwsConnector;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public class AbstractConnectorExecutorTest {
    @Mock
    private AwsConnector mockConnector;

    @Mock
    private Client mockClient;

    @Mock
    private ThreadPool mockThreadPool;

    private ConnectorClientConfig connectorClientConfig;
    private ThreadContext threadContext;
    private AbstractConnectorExecutor executor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        executor = new AwsConnectorExecutor(mockConnector);
        connectorClientConfig = new ConnectorClientConfig();
        threadContext = new ThreadContext(Settings.EMPTY);
    }

    @Test
    public void testValidateWithNullConfig() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(null);
        executor.initialize(mockConnector);
        assertEquals(Integer.valueOf(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE), executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(Integer.valueOf(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE), executor.getConnectorClientConfig().getConnectionTimeoutMillis());
        assertEquals(Integer.valueOf(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE), executor.getConnectorClientConfig().getReadTimeoutMillis());
    }

    @Test
    public void testValidateWithNonNullConfigButNullValues() {
        when(mockConnector.getConnectorClientConfig()).thenReturn(connectorClientConfig);
        executor.initialize(mockConnector);
        assertEquals(Integer.valueOf(ConnectorClientConfig.MAX_CONNECTION_DEFAULT_VALUE), executor.getConnectorClientConfig().getMaxConnections());
        assertEquals(Integer.valueOf(ConnectorClientConfig.CONNECTION_TIMEOUT_DEFAULT_VALUE), executor.getConnectorClientConfig().getConnectionTimeoutMillis());
        assertEquals(Integer.valueOf(ConnectorClientConfig.READ_TIMEOUT_DEFAULT_VALUE), executor.getConnectorClientConfig().getReadTimeoutMillis());
    }

    @Test
    public void testGetMcpRequestHeaders_NullClient() {
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        executor.getMcpRequestHeaders(builder);
        verify(builder, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testGetMcpRequestHeaders_ClientNotSet() {
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        // Client is null by default
        assertNull(executor.getClient());
        executor.getMcpRequestHeaders(builder);

        // No headers should be added when client is null
        verify(builder, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testGetMcpRequestHeaders_WithAllHeaders() {
        // Setup
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        
        threadContext.putHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "test-access-key");
        threadContext.putHeader(MCP_HEADER_AWS_SECRET_ACCESS_KEY, "test-secret-key");
        threadContext.putHeader(MCP_HEADER_AWS_SESSION_TOKEN, "test-session-token");
        threadContext.putHeader(MCP_HEADER_AWS_REGION, "us-west-2");
        threadContext.putHeader(MCP_HEADER_AWS_SERVICE_NAME, "bedrock");
        threadContext.putHeader(MCP_HEADER_OPENSEARCH_URL, "https://localhost:9200");

        executor.setClient(mockClient);
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        // Execute
        executor.getMcpRequestHeaders(builder);

        // Verify all headers were added
        verify(builder).setHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "test-access-key");
        verify(builder).setHeader(MCP_HEADER_AWS_SECRET_ACCESS_KEY, "test-secret-key");
        verify(builder).setHeader(MCP_HEADER_AWS_SESSION_TOKEN, "test-session-token");
        verify(builder).setHeader(MCP_HEADER_AWS_REGION, "us-west-2");
        verify(builder).setHeader(MCP_HEADER_AWS_SERVICE_NAME, "bedrock");
        verify(builder).setHeader(MCP_HEADER_OPENSEARCH_URL, "https://localhost:9200");
    }

    @Test
    public void testGetMcpRequestHeaders_WithPartialHeaders() {
        // Setup - only some headers are set
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        
        threadContext.putHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "test-access-key");
        threadContext.putHeader(MCP_HEADER_AWS_REGION, "us-west-2");

        executor.setClient(mockClient);
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        // Execute
        executor.getMcpRequestHeaders(builder);

        // Verify only set headers were added
        verify(builder).setHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "test-access-key");
        verify(builder).setHeader(MCP_HEADER_AWS_REGION, "us-west-2");
        verify(builder, never()).setHeader(eq(MCP_HEADER_AWS_SECRET_ACCESS_KEY), anyString());
        verify(builder, never()).setHeader(eq(MCP_HEADER_AWS_SESSION_TOKEN), anyString());
        verify(builder, never()).setHeader(eq(MCP_HEADER_AWS_SERVICE_NAME), anyString());
        verify(builder, never()).setHeader(eq(MCP_HEADER_OPENSEARCH_URL), anyString());
    }

    @Test
    public void testGetMcpRequestHeaders_WithEmptyHeaderValues() {
        // Setup - headers exist but are empty
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        
        threadContext.putHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "");
        threadContext.putHeader(MCP_HEADER_AWS_REGION, "us-west-2");

        executor.setClient(mockClient);
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        // Execute
        executor.getMcpRequestHeaders(builder);

        // Verify empty headers are not added
        verify(builder, never()).setHeader(eq(MCP_HEADER_AWS_ACCESS_KEY_ID), anyString());
        verify(builder).setHeader(MCP_HEADER_AWS_REGION, "us-west-2");
    }

    @Test
    public void testGetMcpRequestHeaders_NoHeadersInThreadContext() {
        // Setup - no headers in thread context
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);

        executor.setClient(mockClient);
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);

        // Execute
        executor.getMcpRequestHeaders(builder);

        // Verify no headers were added
        verify(builder, never()).setHeader(anyString(), anyString());
    }

    @Test
    public void testGetMcpRequestHeaders_VerifyHeaderOrder() {
        // Setup
        when(mockClient.threadPool()).thenReturn(mockThreadPool);
        when(mockThreadPool.getThreadContext()).thenReturn(threadContext);
        
        threadContext.putHeader(MCP_HEADER_OPENSEARCH_URL, "https://localhost:9200");
        threadContext.putHeader(MCP_HEADER_AWS_ACCESS_KEY_ID, "test-access-key");

        executor.setClient(mockClient);
        HttpRequest.Builder builder = mock(HttpRequest.Builder.class);
        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);

        // Execute
        executor.getMcpRequestHeaders(builder);

        // Verify headers were added in expected order (access-key-id should come before opensearch-url)
        verify(builder, times(2)).setHeader(headerNameCaptor.capture(), headerValueCaptor.capture());
        assertEquals(MCP_HEADER_AWS_ACCESS_KEY_ID, headerNameCaptor.getAllValues().get(0));
        assertEquals(MCP_HEADER_OPENSEARCH_URL, headerNameCaptor.getAllValues().get(1));
    }
}
