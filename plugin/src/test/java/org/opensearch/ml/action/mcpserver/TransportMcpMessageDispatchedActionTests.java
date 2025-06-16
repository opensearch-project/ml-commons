/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
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
import org.junit.Ignore;
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
    private StreamingRestChannel channel;
    private McpServerSession session;
    private static final String SESSION_ID = "successSessionId";

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

        MLIndicesHandler mlIndicesHandler = mock(MLIndicesHandler.class);
        McpToolsHelper mcpToolsHelper = mock(McpToolsHelper.class);
        McpAsyncServerHolder.init(mlIndicesHandler, mcpToolsHelper);
        channel = mock(StreamingRestChannel.class);
        McpAsyncServerHolder.CHANNELS.put(SESSION_ID, channel);
        McpAsyncServerHolder.CHANNELS.put(SESSION_ID, channel);
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLMcpSessionManagementIndex(isA(ActionListener.class));
        doAnswer(invocationOnMock -> {
            ActionListener<Boolean> listener = invocationOnMock.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mcpToolsHelper).autoLoadAllMcpTools(isA(ActionListener.class));

        McpServerSession.Factory sessionFactory = mock(McpServerSession.Factory.class);
        session = mock(McpServerSession.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(session.handle(any())).thenReturn(Mono.empty());
        when(session.sendNotification(any(), any())).thenReturn(Mono.empty());
        when(sessionFactory.create(any(McpServerTransport.class))).thenReturn(session);
        McpAsyncServerHolder.getMcpServerTransportProviderInstance().setSessionFactory(sessionFactory);
        doAnswer(invocationOnMock -> {
            ActionListener<IndexResponse> listener = invocationOnMock.getArgument(1);
            IndexResponse response = mock(IndexResponse.class);
            when(response.status()).thenReturn(RestStatus.CREATED);
            listener.onResponse(response);
            return null;
        }).when(client).index(any(), isA(ActionListener.class));
        McpAsyncServerHolder.getMcpServerTransportProviderInstance().handleSseConnection(channel, false, "mockNodeId", client).subscribe();
    }

    @Ignore
    @Test
    public void test_doExecute_successful() {
        MLMcpMessageRequest request = mock(MLMcpMessageRequest.class);
        when(request.getSessionId()).thenReturn(SESSION_ID);
        when(request.getRequestBody()).thenReturn(requestBody);
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", SESSION_ID))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        transportMcpMessageDispatchedAction.doExecute(mock(Task.class), request, listener);
        verify(listener).onResponse(new AcknowledgedResponse(true));
    }

    @Test
    public void test_doExecute_failure() throws IOException {
        MLMcpMessageRequest request = mock(MLMcpMessageRequest.class);
        when(session.getId()).thenReturn(SESSION_ID);
        when(request.getSessionId()).thenReturn(SESSION_ID);
        when(request.getRequestBody()).thenReturn("invalid request body");
        BytesReference bytesReference = BytesReference
            .fromByteBuffer(ByteBuffer.wrap("invalid request body".getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", SESSION_ID))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        when(channel.newErrorBuilder()).thenReturn(XContentBuilder.builder(XContentType.JSON.xContent()));
        doNothing().when(channel).sendResponse(any());
        transportMcpMessageDispatchedAction.doExecute(mock(Task.class), request, listener);
        verify(listener).onResponse(new AcknowledgedResponse(true));
    }

    @Test
    public void test_sendErrorResponse() throws IOException {
        BytesReference bytesReference = BytesReference.fromByteBuffer(ByteBuffer.wrap(requestBody.getBytes(StandardCharsets.UTF_8)));
        RestRequest restRequest = new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
            .withParams(ImmutableMap.of("sessionId", SESSION_ID))
            .withContent(bytesReference, MediaType.fromMediaType(XContentType.JSON.mediaType()))
            .build();
        when(channel.detailedErrorsEnabled()).thenReturn(false);
        when(channel.request()).thenReturn(restRequest);
        when(channel.newErrorBuilder()).thenReturn(XContentBuilder.builder(XContentType.JSON.xContent()));
        doThrow(new RuntimeException(new IOException("test exception"))).when(channel).sendResponse(any());
        transportMcpMessageDispatchedAction.sendErrorResponse(listener, channel, new RuntimeException("test exception"));
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertNotNull(argumentCaptor.getValue());
        assertTrue(argumentCaptor.getValue().getCause() instanceof RuntimeException);
        assertTrue(argumentCaptor.getValue().getCause().getMessage().contains("test exception"));
    }

}
