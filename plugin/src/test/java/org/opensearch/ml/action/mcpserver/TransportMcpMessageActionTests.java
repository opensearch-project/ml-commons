package org.opensearch.ml.action.mcpserver;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.utils.TestHelper.mockClientStashContext;
import static org.opensearch.ml.utils.TestHelper.setupTestClusterState;

import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.transport.mcpserver.requests.message.MLMcpMessageRequest;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportException;
import org.opensearch.transport.TransportResponseHandler;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class TransportMcpMessageActionTests extends OpenSearchTestCase {

    @Mock
    TransportService transportService;
    @Mock
    ClusterService clusterService;
    @Mock
    ThreadPool threadPool;
    @Mock
    Client client;
    @Mock
    NamedXContentRegistry xContentRegistry;
    @Mock
    ActionFilters actionFilters;

    private TransportMcpMessageAction transportMcpMessageAction;

    @Mock
    Task task;

    @Mock
    private ActionListener<AcknowledgedResponse> listener;

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
        transportMcpMessageAction = new TransportMcpMessageAction(
            transportService,
            actionFilters,
            clusterService,
            threadPool,
            client,
            xContentRegistry
        );
    }

    public void test_doExecute_successful() {
        doAnswer(invocationOnMock -> {
            TransportResponseHandler<AcknowledgedResponse> handler = invocationOnMock.getArgument(3);
            handler.handleResponse(new AcknowledgedResponse(true));
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), isA(TransportResponseHandler.class));
        MLMcpMessageRequest request = MLMcpMessageRequest
            .builder()
            .nodeId("mockNodeId")
            .sessionId("mockSessionId")
            .requestBody("mockRequestBody")
            .build();
        transportMcpMessageAction.doExecute(task, request, listener);
        ArgumentCaptor<AcknowledgedResponse> argumentCaptor = ArgumentCaptor.forClass(AcknowledgedResponse.class);
        verify(listener).onResponse(argumentCaptor.capture());
        assertEquals(true, argumentCaptor.getValue().isAcknowledged());
    }

    public void test_doExecute_exception() {
        doAnswer(invocationOnMock -> {
            TransportResponseHandler<AcknowledgedResponse> handler = invocationOnMock.getArgument(3);
            handler.handleException(new TransportException("mock exception"));
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), isA(TransportResponseHandler.class));
        MLMcpMessageRequest request = MLMcpMessageRequest
            .builder()
            .nodeId("mockNodeId")
            .sessionId("mockSessionId")
            .requestBody("mockRequestBody")
            .build();
        transportMcpMessageAction.doExecute(task, request, listener);
        ArgumentCaptor<TransportException> argumentCaptor = ArgumentCaptor.forClass(TransportException.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("mock exception", argumentCaptor.getValue().getMessage());
    }

    public void test_doExecute_clientException() {
        when(client.threadPool()).thenThrow(new RuntimeException("mock exception"));
        MLMcpMessageRequest request = MLMcpMessageRequest.builder().nodeId("mockNodeId").sessionId("mockSessionId").requestBody("mockRequestBody").build();
        transportMcpMessageAction.doExecute(task, request, listener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals("mock exception", argumentCaptor.getValue().getMessage());
    }

}
