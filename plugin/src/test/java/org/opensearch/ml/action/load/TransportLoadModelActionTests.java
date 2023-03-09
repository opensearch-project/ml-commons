/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.load;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.load.LoadModelNodesResponse;
import org.opensearch.ml.common.transport.load.LoadModelResponse;
import org.opensearch.ml.common.transport.load.MLLoadModelRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
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

import com.google.common.collect.ImmutableMap;

public class TransportLoadModelActionTests extends OpenSearchTestCase {
    @Mock
    private MLTaskManager mlTaskManager;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private ClusterService clusterService;

    @Mock
    private ThreadPool threadPool;

    @Mock
    private Client client;

    @Mock
    private DiscoveryNodeHelper nodeFilter;

    @Mock
    private MLModelManager mlModelManager;

    @Mock
    private MLStats mlStats;

    @Mock
    private MLLoadModelRequest mlLoadModelRequest;

    @InjectMocks
    private TransportLoadModelAction transportLoadModelAction;
    @Mock
    private ExecutorService executorService;

    @Mock
    MLTask mlTask;
    private final String modelId = "mock_model_id";
    private final MLModel mlModel = mock(MLModel.class);
    private final String localNodeId = "mockNodeId";
    private MLEngine mlEngine;
    private ModelHelper modelHelper;

    private final List<DiscoveryNode> eligibleNodes = mock(List.class);

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)));
        modelHelper = new ModelHelper(mlEngine);
        when(mlLoadModelRequest.getModelId()).thenReturn("mockModelId");
        when(mlLoadModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1" });
        DiscoveryNode discoveryNode = mock(DiscoveryNode.class);
        DiscoveryNode[] discoveryNodes = new DiscoveryNode[] { discoveryNode };
        when(nodeFilter.getEligibleNodes()).thenReturn(discoveryNodes);
        when(discoveryNode.getId()).thenReturn("node1");

        when(clusterService.localNode()).thenReturn(discoveryNode);
        when(client.threadPool()).thenReturn(threadPool);

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        executorService = mock(ExecutorService.class);
        when(threadPool.executor(anyString())).thenReturn(executorService);

        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(eq(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT))).thenReturn(mlStat);
    }

    public void testDoExecute_success() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockIndexId");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        ActionListener<LoadModelResponse> loadModelResponseListener = mock(ActionListener.class);
        transportLoadModelAction.doExecute(mock(Task.class), mlLoadModelRequest, loadModelResponseListener);
        verify(loadModelResponseListener).onResponse(any(LoadModelResponse.class));
    }

    public void testDoExecute_whenLoadModelRequestNodeIdsEmpty_thenMLResourceNotFoundException() {
        DiscoveryNodeHelper nodeHelper = mock(DiscoveryNodeHelper.class);
        when(nodeHelper.getEligibleNodes()).thenReturn(new DiscoveryNode[] {});
        TransportLoadModelAction loadModelAction = spy(
            new TransportLoadModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                mock(NamedXContentRegistry.class),
                nodeHelper,
                mock(MLTaskDispatcher.class),
                mlModelManager,
                mlStats
            )
        );
        MLLoadModelRequest mlLoadModelRequest1 = mock(MLLoadModelRequest.class);
        when(mlLoadModelRequest1.getModelNodeIds()).thenReturn(null);
        ActionListener<LoadModelResponse> loadModelResponseListener = mock(ActionListener.class);
        loadModelAction.doExecute(mock(Task.class), mlLoadModelRequest1, loadModelResponseListener);
        verify(loadModelResponseListener).onFailure(any(IllegalArgumentException.class));
    }

    public void testDoExecute_whenGetModelHasNPE_exception() {
        doThrow(NullPointerException.class)
            .when(mlModelManager)
            .getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        ActionListener<LoadModelResponse> loadModelResponseListener = mock(ActionListener.class);
        transportLoadModelAction.doExecute(mock(Task.class), mlLoadModelRequest, loadModelResponseListener);
        verify(loadModelResponseListener).onFailure(any(Exception.class));
    }

    public void testDoExecute_whenThreadPoolExecutorException_TaskRemoved() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(3);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockIndexId");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));
        when(mlTaskManager.contains(anyString())).thenReturn(true);

        doThrow(RuntimeException.class).when(threadPool).executor(anyString());

        ActionListener<LoadModelResponse> loadModelResponseListener = mock(ActionListener.class);
        transportLoadModelAction.doExecute(mock(Task.class), mlLoadModelRequest, loadModelResponseListener);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

    public void testUpdateModelLoadStatusAndTriggerOnNodesAction_success() throws NoSuchFieldException, IllegalAccessException {
        Field clientField = MLModelManager.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mlModelManager, client);

        doCallRealMethod().when(mlModelManager).updateModel(anyString(), any(ImmutableMap.class), isA(ActionListener.class));

        LoadModelNodesResponse loadModelNodesResponse = mock(LoadModelNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<LoadModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(loadModelNodesResponse);
            return null;
        }).when(client).execute(any(ActionType.class), any(ActionRequest.class), isA(ActionListener.class));

        UpdateResponse updateResponse = mock(UpdateResponse.class);
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(1);
            listener.onResponse(updateResponse);
            return null;
        }).when(client).update(any(UpdateRequest.class), isA(ActionListener.class));

        when(mlTaskManager.contains(anyString())).thenReturn(true);

        DiscoveryNode discoveryNode = mock(DiscoveryNode.class);
        when(discoveryNode.getId()).thenReturn("node1");
        transportLoadModelAction
            .updateModelLoadStatusAndTriggerOnNodesAction(
                modelId,
                "mock_task_id",
                mlModel,
                localNodeId,
                mlTask,
                Arrays.asList(discoveryNode),
                FunctionName.ANOMALY_LOCALIZATION
            );
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager).updateModel(anyString(), captor.capture(), any());
        Map<String, Object> map = captor.getValue();
        assertNotNull(map.get(MLModel.PLANNING_WORKER_NODES_FIELD));
        assertEquals(1, (((List) map.get(MLModel.PLANNING_WORKER_NODES_FIELD)).size()));
    }

    public void testUpdateModelLoadStatusAndTriggerOnNodesAction_whenMLTaskManagerThrowException_ListenerOnFailureExecuted() {
        doCallRealMethod().when(mlModelManager).updateModel(anyString(), any(ImmutableMap.class), isA(ActionListener.class));
        transportLoadModelAction
            .updateModelLoadStatusAndTriggerOnNodesAction(
                modelId,
                "mock_task_id",
                mlModel,
                localNodeId,
                mlTask,
                eligibleNodes,
                FunctionName.TEXT_EMBEDDING
            );
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

}
