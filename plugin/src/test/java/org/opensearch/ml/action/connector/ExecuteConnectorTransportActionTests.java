/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorRequest;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class ExecuteConnectorTransportActionTests extends OpenSearchTestCase {

    private ExecuteConnectorTransportAction action;

    @Mock
    private Client client;

    @Mock
    ActionListener<MLTaskResponse> actionListener;
    @Mock
    private ClusterService clusterService;
    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private ScriptService scriptService;
    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private Metadata metaData;
    @Mock
    private ConnectorAccessControlHelper connectorAccessControlHelper;
    @Mock
    private MLExecuteConnectorRequest request;
    @Mock
    private EncryptorImpl encryptor;
    @Mock
    private HttpConnector connector;
    @Mock
    private Task task;
    @Mock
    ThreadPool threadPool;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);

        ClusterState testState = new ClusterState(
            new ClusterName("clusterName"),
            123l,
            "111111",
            metaData,
            null,
            null,
            null,
            Map.of(),
            0,
            false
        );
        when(clusterService.state()).thenReturn(testState);

        when(request.getConnectorId()).thenReturn("test_connector_id");

        Settings settings = Settings.builder().build();
        ThreadContext threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        action = new ExecuteConnectorTransportAction(
            transportService,
            actionFilters,
            client,
            clusterService,
            scriptService,
            xContentRegistry,
            connectorAccessControlHelper,
            encryptor,
            mlFeatureEnabledSetting
        );
    }

    public void testExecute_NoConnectorIndex() {
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assertTrue(argCaptor.getValue().getMessage().contains("Can't find connector test_connector_id"));
    }

    public void testExecute_FailedToGetConnector() {
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);
        when(metaData.hasIndex(anyString())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onFailure(new RuntimeException("test failure"));
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assertTrue(argCaptor.getValue().getMessage().contains("test failure"));
    }

    public void testExecute_NullMLInput() {
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);
        when(metaData.hasIndex(anyString())).thenReturn(true);
        when(connector.getProtocol()).thenReturn(ConnectorProtocols.HTTP);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
    }

}
