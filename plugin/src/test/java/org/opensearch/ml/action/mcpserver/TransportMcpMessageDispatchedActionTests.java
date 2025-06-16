/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.mockClientStashContext;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.MediaType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.StreamingRestChannel;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransport;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Log4j2
public class TransportMcpMessageDispatchedActionTests extends OpenSearchTestCase {

    @Mock
    private TransportService transportService;
    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private Client client;
    @Mock
    private NamedXContentRegistry xContentRegistry;
    @Mock
    private DiscoveryNodeHelper nodeFilter;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ActionListener<AcknowledgedResponse> listener;
    @Mock
    private MLIndicesHandler mlIndicesHandler;
    @Mock
    private McpToolsHelper mcpToolsHelper;

    private final String requestBody = """
        {
            "jsonrpc": "2.0",
            "id": "110",
            "method": "tools/call",
            "params": {
                "name": "IndexMappingTool",
                "arguments": {
                    "index": [
                        "test"
                    ]
                }
            }
        }
        """;
    private TransportMcpMessageDispatchedAction transportMcpMessageDispatchedAction;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED.getKey(), true).build();
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MCP_SERVER_ENABLED)));
        mockClientStashContext(client, settings);
        when(clusterService.state()).thenReturn(setupTestClusterState("node"));
        transportMcpMessageDispatchedAction = new TransportMcpMessageDispatchedAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry,
            nodeFilter,
            mlFeatureEnabledSetting
        );
        when(mlFeatureEnabledSetting.isMcpServerEnabled()).thenReturn(true);
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
    }

    @Test
    public void test_doExecute_successful() {
        MLMcpMessageRequest request = mock(MLMcpMessageRequest.class);
        String sessionId = "randomGeneratedUUID1";
        when(request.getSessionId()).thenReturn(sessionId);
        when(request.getRequestBody()).thenReturn(requestBody);
        StreamingRestChannel channel = mock(StreamingRestChannel.class);
        McpAsyncServerHolder.CHANNELS.put("sessionId", channel);
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", sessionId))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        initMockSession(channel, sessionId);
        transportMcpMessageDispatchedAction.doExecute(mock(Task.class), request, listener);
        verify(listener).onResponse(new AcknowledgedResponse(true));
    }

    @Test
    public void test_doExecute_failure() throws IOException {
        MLMcpMessageRequest request = mock(MLMcpMessageRequest.class);
        String sessionId = "randomGeneratedUUID2";
        when(request.getSessionId()).thenReturn(sessionId);
        when(request.getRequestBody()).thenReturn("invalid request body");
        StreamingRestChannel channel = mock(StreamingRestChannel.class);
        McpAsyncServerHolder.CHANNELS.put("sessionId", channel);
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap("invalid request body".getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", sessionId))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        when(channel.newErrorBuilder()).thenReturn(XContentBuilder.builder(XContentType.JSON.xContent()));
        initMockSession(channel, sessionId);
        transportMcpMessageDispatchedAction.doExecute(mock(Task.class), request, listener);
        verify(listener).onResponse(new AcknowledgedResponse(true));
    }

    @Test
    public void test_sendErrorResponse() throws IOException {
        StreamingRestChannel channel = mock(StreamingRestChannel.class);
        String sessionId = "randomGeneratedUUID3";
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", sessionId))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        when(channel.newErrorBuilder()).thenReturn(XContentBuilder.builder(XContentType.JSON.xContent()));
        initMockSession(channel, sessionId);
        doThrow(new RuntimeException(new IOException("test exception"))).when(channel).sendResponse(any());
        transportMcpMessageDispatchedAction.sendErrorResponse(listener, channel, new RuntimeException("test exception"));
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertTrue(argumentCaptor.getValue().getCause() instanceof RuntimeException);
        assertTrue(argumentCaptor.getValue().getCause().getMessage().contains("test exception"));
    }

    private void initMockSession(StreamingRestChannel channel, String sessionId) {
        McpServerSession.Factory sessionFactory = mock(McpServerSession.Factory.class);
        McpServerSession session = mock(McpServerSession.class);
        when(session.handle(any())).thenReturn(Mono.empty());
        when(session.getId()).thenReturn(sessionId);
        when(sessionFactory.create(any(McpServerTransport.class))).thenReturn(session);
        McpAsyncServerHolder.getMcpServerTransportProviderInstance().setSessionFactory(sessionFactory);
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLMcpSessionManagementIndex(isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<IndexResponse> listener = invocationOnMock.getArgument(1);
            IndexResponse response = mock(IndexResponse.class);
            when(response.status()).thenReturn(RestStatus.CREATED);
            listener.onResponse(response);
            return null;
        }).when(client).index(any(), isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpToolsHelper).autoLoadAllMcpTools(isA(ActionListener.class));
        McpAsyncServerHolder.getMcpServerTransportProviderInstance().handleSseConnection(channel, false, "mockNodeId", client).subscribe();
    }
}
