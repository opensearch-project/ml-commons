/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.register;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_URL_REGEX;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelRequest;
import org.opensearch.ml.common.transport.register.MLRegisterModelResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class TransportRegisterModelActionTests extends OpenSearchTestCase {
    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Mock
    private TransportService transportService;

    @Mock
    private ModelHelper modelHelper;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private ClusterService clusterService;

    private Settings settings;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private DiscoveryNodeHelper nodeFilter;

    @Mock
    private MLTaskDispatcher mlTaskDispatcher;

    @Mock
    private MLStats mlStats;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLRegisterModelResponse> actionListener;

    @Mock
    private DiscoveryNode node1;

    @Mock
    private DiscoveryNode node2;

    @Mock
    private IndexResponse indexResponse;

    ThreadContext threadContext;

    private TransportRegisterModelAction transportRegisterModelAction;

    private String trustedUrlRegex = "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_TRUSTED_URL_REGEX.getKey(), trustedUrlRegex).build();
        threadContext = new ThreadContext(settings);
        ClusterSettings clusterSettings = clusterSetting(settings, ML_COMMONS_TRUSTED_URL_REGEX);
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        transportRegisterModelAction = new TransportRegisterModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlIndicesHandler,
            mlModelManager,
            mlTaskManager,
            clusterService,
            settings,
            threadPool,
            client,
            nodeFilter,
            mlTaskDispatcher,
            mlStats,
            modelAccessControlHelper
        );
        assertNotNull(transportRegisterModelAction);

        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(eq(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT))).thenReturn(mlStat);

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(), any());

        doAnswer(invocation -> {
            ActionListener<DiscoveryNode> listener = invocation.getArgument(0);
            listener.onResponse(node1);
            return null;
        }).when(mlTaskDispatcher).dispatch(any());

        when(clusterService.localNode()).thenReturn(node2);

        doAnswer(invocation -> { return null; }).when(mlModelManager).registerMLModel(any(), any());

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    @Ignore
    public void testDoExecute_successWithLocalNodeEqualToClusterNode() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId1");

        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());
        transportRegisterModelAction.doExecute(task, prepareRequest(), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Ignore
    public void testDoExecute_invalidURL() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("URL can't match trusted url regex");
        transportRegisterModelAction.doExecute(task, prepareRequest("test url"), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Ignore
    public void testDoExecute_successWithLocalNodeNotEqualToClusterNode() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId2");
        MLForwardResponse forwardResponse = Mockito.mock(MLForwardResponse.class);
        doAnswer(invocation -> {
            ActionListenerResponseHandler<MLForwardResponse> handler = invocation.getArgument(3);
            handler.handleResponse(forwardResponse);
            return null;
        }).when(transportService).sendRequest(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest(), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Ignore
    public void testDoExecute_FailToSendForwardRequest() {
        when(node1.getId()).thenReturn("NodeId1");
        when(node2.getId()).thenReturn("NodeId2");
        doThrow(new RuntimeException("error")).when(transportService).sendRequest(any(), any(), any(), any());

        transportRegisterModelAction.doExecute(task, prepareRequest(), actionListener);
        ArgumentCaptor<MLRegisterModelResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterModelResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    @Ignore
    public void testTransportRegisterModelActionDoExecuteWithDispatchException() {
        doAnswer(invocation -> {
            ActionListener<Exception> listener = invocation.getArgument(0);
            listener.onFailure(new Exception("Failed to dispatch register model task "));
            return null;
        }).when(mlTaskDispatcher).dispatch(any());
        when(node1.getId()).thenReturn("NodeId1");
        when(clusterService.localNode()).thenReturn(node1);
        transportRegisterModelAction.doExecute(task, prepareRequest(), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Ignore
    public void testTransportRegisterModelActionDoExecuteWithCreateTaskException() {
        doAnswer(invocation -> {
            ActionListener<Exception> listener = invocation.getArgument(1);
            listener.onFailure(new Exception("Failed to create register model task"));
            return null;
        }).when(mlTaskManager).createMLTask(any(), any());
        when(node1.getId()).thenReturn("NodeId1");
        when(clusterService.localNode()).thenReturn(node1);
        transportRegisterModelAction.doExecute(task, prepareRequest(), actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
    }

    @Ignore
    private MLRegisterModelRequest prepareRequest() {
        return prepareRequest("http://test_url");
    }

    @Ignore
    private MLRegisterModelRequest prepareRequest(String url) {
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.BATCH_RCF)
            .deployModel(true)
            .version("1.0")
            .modelName("Test Model")
            .modelConfig(
                new TextEmbeddingModelConfig(
                    "CUSTOM",
                    123,
                    TextEmbeddingModelConfig.FrameworkType.SENTENCE_TRANSFORMERS,
                    "all config",
                    TextEmbeddingModelConfig.PoolingMode.MEAN,
                    true,
                    512
                )
            )
            .modelFormat(MLModelFormat.TORCH_SCRIPT)
            .url(url)
            .build();
        return new MLRegisterModelRequest(registerModelInput);
    }

}
