/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.connector.CertificateProcessor;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.McpStreamableHttpConnector;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.httpclient.MLSslContextFactory;
import org.opensearch.ml.engine.MLStaticMockBase;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

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
        when(builder.jsonSchemaValidator(any())).thenReturn(builder);
        when(builder.build()).thenReturn(mcpClient);
    }

    @Test
    public void getMcpToolSpecs_returnsExpectedSpecs() {

        String inputSchemaJSON =
            "{\"type\":\"object\",\"properties\":{\"state\":{\"title\":\"State\",\"type\":\"string\"}},\"required\":[\"state\"],\"additionalProperties\":false}";

        McpSchema.Tool tool = McpSchema.Tool
            .builder()
            .name("tool1")
            .description("desc1")
            .inputSchema(new JacksonMcpJsonMapper(JsonMapper.shared()), inputSchemaJSON)
            .build();
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

    /** Config the executor handed to MLSslContextFactory, captured for assertions. */
    private ConnectorClientConfig capturedConfig;

    /**
     * Drives getMcpToolSpecs with MLSslContextFactory stubbed, applies the captured customizeClient
     * consumer to a mock HttpClient.Builder, and returns that builder.
     *
     * <p>Stubbing the factory keeps these tests about wiring: the exact instance the factory returns
     * must be the one installed on the client. What the factory produces for a given config is
     * covered by MLSslContextFactoryTest and MLSslContextFactoryTlsHandshakeTest.
     */
    private HttpClient.Builder captureClientCustomization(SSLContext factoryResult) {
        HttpClient.Builder clientBuilder = mock(HttpClient.Builder.class);
        HttpClientStreamableHttpTransport.Builder transportBuilder = mock(HttpClientStreamableHttpTransport.Builder.class);

        when(transportBuilder.jsonMapper(any())).thenReturn(transportBuilder);
        when(transportBuilder.endpoint(anyString())).thenReturn(transportBuilder);
        when(transportBuilder.connectTimeout(any())).thenReturn(transportBuilder);
        when(transportBuilder.customizeClient(any())).thenReturn(transportBuilder);
        when(transportBuilder.customizeRequest(any())).thenReturn(transportBuilder);
        when(transportBuilder.build()).thenReturn(mock(HttpClientStreamableHttpTransport.class));

        when(mcpClient.initialize()).thenReturn(null);
        when(mcpClient.listTools()).thenReturn(new McpSchema.ListToolsResult(List.of(), null));

        ArgumentCaptor<ConnectorClientConfig> configCaptor = ArgumentCaptor.forClass(ConnectorClientConfig.class);

        try (
            MockedStatic<MLSslContextFactory> mockedFactory = mockStatic(MLSslContextFactory.class);
            MockedStatic<HttpClientStreamableHttpTransport> mockedTransport = mockStatic(HttpClientStreamableHttpTransport.class);
            MockedStatic<McpClient> mockedClient = mockStatic(McpClient.class)
        ) {
            mockedFactory.when(() -> MLSslContextFactory.create(any(), any(), any())).thenReturn(factoryResult);
            mockedTransport.when(() -> HttpClientStreamableHttpTransport.builder(anyString())).thenReturn(transportBuilder);
            mockedClient.when(() -> McpClient.sync(any(McpClientTransport.class))).thenReturn(builder);

            new McpStreamableHttpConnectorExecutor(mockConnector).getMcpToolSpecs();

            mockedFactory.verify(() -> MLSslContextFactory.create(configCaptor.capture(), any(), any()));
        }
        capturedConfig = configCaptor.getValue();

        // The SDK overwrites connectTimeout in build(), so it must be set on the transport builder.
        verify(transportBuilder).connectTimeout(Duration.ofSeconds(30));

        ArgumentCaptor<Consumer<HttpClient.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(transportBuilder).customizeClient(captor.capture());
        captor.getValue().accept(clientBuilder);

        // Asserted only after accept(), otherwise the mock has no interactions and this cannot fail.
        verify(clientBuilder, never()).connectTimeout(any());
        return clientBuilder;
    }

    @Test
    public void getMcpToolSpecs_defaultConfig_doesNotOverrideSslContext() {
        HttpClient.Builder clientBuilder = captureClientCustomization(null);

        verify(clientBuilder).followRedirects(any());
        verify(clientBuilder, never()).sslContext(any());
        Assert.assertNotEquals(Boolean.TRUE, capturedConfig.getMutualTlsEnabled());
        Assert.assertNotEquals(Boolean.TRUE, capturedConfig.getSkipSslVerification());
    }

    @Test
    public void getMcpToolSpecs_skipSslVerification_installsSslContext() throws Exception {
        when(mockConnector.getConnectorClientConfig())
            .thenReturn(ConnectorClientConfig.builder().connectionTimeout(30).readTimeout(30).skipSslVerification(true).build());
        SSLContext expected = SSLContext.getInstance("TLS");
        expected.init(null, null, null);

        HttpClient.Builder clientBuilder = captureClientCustomization(expected);

        // The exact instance the factory produced must be the one installed.
        verify(clientBuilder).sslContext(same(expected));
        Assert.assertEquals(Boolean.TRUE, capturedConfig.getSkipSslVerification());
    }

    @Test
    public void getMcpToolSpecs_validMutualTlsConfig_installsSslContext() throws Exception {
        Map<String, String> credentials = new HashMap<>();
        credentials.put(CertificateProcessor.CLIENT_CERT_PEM_FIELD, readFixture("mtls-client-cert.pem"));
        credentials.put(CertificateProcessor.CLIENT_KEY_PEM_FIELD, readFixture("mtls-client-key.pem"));

        when(mockConnector.getDecryptedCredential()).thenReturn(credentials);
        when(mockConnector.getConnectorClientConfig())
            .thenReturn(
                ConnectorClientConfig.builder().connectionTimeout(30).readTimeout(30).mutualTlsEnabled(true).keystoreType("PEM").build()
            );
        SSLContext expected = SSLContext.getInstance("TLS");
        expected.init(null, null, null);

        HttpClient.Builder clientBuilder = captureClientCustomization(expected);

        verify(clientBuilder).sslContext(same(expected));
        Assert.assertEquals(Boolean.TRUE, capturedConfig.getMutualTlsEnabled());
    }

    private String readFixture(String filename) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/certificates/" + filename)) {
            Assert.assertNotNull("Missing test fixture on classpath: /certificates/" + filename, in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void getMcpToolSpecs_invalidMutualTlsConfig_throwsActionableValidationException() {
        when(mockConnector.getDecryptedCredential()).thenReturn(new HashMap<>());
        when(mockConnector.getConnectorClientConfig())
            .thenReturn(
                ConnectorClientConfig.builder().connectionTimeout(30).readTimeout(30).mutualTlsEnabled(true).keystoreType("PEM").build()
            );

        McpStreamableHttpConnectorExecutor exec = new McpStreamableHttpConnectorExecutor(mockConnector);

        MLValidationException exception = assertThrows(MLValidationException.class, exec::getMcpToolSpecs);
        Assert
            .assertFalse(
                "Validation detail must not be hidden behind the generic MCP error: " + exception.getMessage(),
                exception.getMessage().contains("Unexpected error while getting MCP tools")
            );
    }

}
