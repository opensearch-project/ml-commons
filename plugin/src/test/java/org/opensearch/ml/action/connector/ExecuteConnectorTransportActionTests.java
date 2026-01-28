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
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.connector.MLExecuteConnectorRequest;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.script.ScriptService;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private HttpConnector connector;
    @Mock
    private Task task;
    @Mock
    ThreadPool threadPool;

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
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(Map.of(), null))
            .build());
        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assertTrue(argCaptor.getValue().getMessage().contains("Can't find connector test_connector_id"));
    }

    public void testExecute_FailedToGetConnector() {
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);
        when(metaData.hasIndex(anyString())).thenReturn(true);
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(Map.of(), null))
            .build());

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
        // Test the early return when MLInput is null (line 77-80)
        when(request.getMlInput()).thenReturn(null);

        action.doExecute(task, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assertTrue(argCaptor.getValue().getMessage().contains("MLInput cannot be null"));
    }

    public void testExecute_AccessDenied() {
        when(metaData.hasIndex(anyString())).thenReturn(true);
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(Map.of(), null))
            .build());
        when(connector.getProtocol()).thenReturn(ConnectorProtocols.HTTP);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        // Connector access validation returns false
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(false);

        action.doExecute(task, request, actionListener);

        // When access is denied, no action should be taken on the listener
        verify(actionListener, times(0)).onResponse(any());
        verify(actionListener, times(0)).onFailure(any());
    }

    public void testExecute_WithCustomConnectorAction() {
        when(metaData.hasIndex(anyString())).thenReturn(true);
        // Set custom connector action in parameters
        Map<String, String> params = new java.util.HashMap<>();
        params.put("connector_action", "CUSTOM_ACTION");
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(params, null))
            .build());
        when(connector.getProtocol()).thenReturn(ConnectorProtocols.HTTP);
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        action.doExecute(task, request, actionListener);

        // Verify that getConnector was called
        verify(connectorAccessControlHelper).getConnector(eq(client), eq("test_connector_id"), any());
    }

    public void testExecute_SuccessfulExecution() {
        when(metaData.hasIndex(anyString())).thenReturn(true);
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(Map.of(), null))
            .build());
        when(connector.getProtocol()).thenReturn(ConnectorProtocols.HTTP);
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        action.doExecute(task, request, actionListener);

        // Verify connector access was validated
        verify(connectorAccessControlHelper).validateConnectorAccess(eq(client), eq(connector));
    }

    public void testExecute_WithNullParameters() {
        when(metaData.hasIndex(anyString())).thenReturn(true);
        // Test with null parameters to cover the null check branch
        when(request.getMlInput()).thenReturn(org.opensearch.ml.common.input.MLInput.builder()
            .algorithm(org.opensearch.ml.common.FunctionName.REMOTE)
            .inputDataset(new org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet(null, null))
            .build());
        when(connector.getProtocol()).thenReturn(ConnectorProtocols.HTTP);
        when(connectorAccessControlHelper.validateConnectorAccess(eq(client), any())).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<Connector> listener = invocation.getArgument(2);
            listener.onResponse(connector);
            return null;
        }).when(connectorAccessControlHelper).getConnector(eq(client), anyString(), any());

        action.doExecute(task, request, actionListener);

        verify(connectorAccessControlHelper).getConnector(eq(client), eq("test_connector_id"), any());
    }

}
