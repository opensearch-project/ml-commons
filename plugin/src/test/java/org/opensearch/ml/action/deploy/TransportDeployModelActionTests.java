/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.deploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
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
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.helper.ModelAccessControlHelper;
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

public class TransportDeployModelActionTests extends OpenSearchTestCase {
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
    private MLDeployModelRequest mlDeployModelRequest;

    private TransportDeployModelAction transportDeployModelAction;
    @Mock
    private ExecutorService executorService;

    @Mock
    MLTask mlTask;
    @Mock
    MLTaskDispatcher mlTaskDispatcher;
    @Mock
    NamedXContentRegistry namedXContentRegistry;
    private Settings settings;
    private ClusterSettings clusterSettings;
    private final String modelId = "mock_model_id";
    private final MLModel mlModel = mock(MLModel.class);
    private final String localNodeId = "mockNodeId";
    private MLEngine mlEngine;
    private ModelHelper modelHelper;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    private final List<DiscoveryNode> eligibleNodes = mock(List.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true).build();
        clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN)));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);

        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)));
        modelHelper = new ModelHelper(mlEngine);
        when(mlDeployModelRequest.getModelId()).thenReturn("mockModelId");
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1" });
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
        transportDeployModelAction = new TransportDeployModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            clusterService,
            threadPool,
            client,
            namedXContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            mlStats,
            settings,
            modelAccessControlHelper
        );
    }

    @Ignore
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

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        verify(deployModelResponseListener).onResponse(any(MLDeployModelResponse.class));
    }

    @Ignore
    public void testDoExecute_DoNotAllowCustomDeploymentPlan() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Don't allow custom deployment plan");
        Settings settings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false).build();
        ClusterSettings clusterSettings = new ClusterSettings(
            settings,
            new HashSet<>(Arrays.asList(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN))
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        TransportDeployModelAction transportDeployModelAction = new TransportDeployModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            clusterService,
            threadPool,
            client,
            namedXContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            mlStats,
            settings,
            modelAccessControlHelper
        );

        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, mock(ActionListener.class));
    }

    @Ignore
    public void testDoExecute_whenDeployModelRequestNodeIdsEmpty_thenMLResourceNotFoundException() {
        DiscoveryNodeHelper nodeHelper = mock(DiscoveryNodeHelper.class);
        when(nodeHelper.getEligibleNodes()).thenReturn(new DiscoveryNode[] {});
        TransportDeployModelAction deployModelAction = spy(
            new TransportDeployModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                namedXContentRegistry,
                nodeHelper,
                mlTaskDispatcher,
                mlModelManager,
                mlStats,
                settings,
                modelAccessControlHelper
            )
        );
        MLDeployModelRequest MLDeployModelRequest1 = mock(MLDeployModelRequest.class);
        when(MLDeployModelRequest1.getModelNodeIds()).thenReturn(null);
        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        deployModelAction.doExecute(mock(Task.class), MLDeployModelRequest1, deployModelResponseListener);
        verify(deployModelResponseListener).onFailure(any(IllegalArgumentException.class));
    }

    @Ignore
    public void testDoExecute_whenGetModelHasNPE_exception() {
        doThrow(NullPointerException.class)
            .when(mlModelManager)
            .getModel(anyString(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        verify(deployModelResponseListener).onFailure(any(Exception.class));
    }

    @Ignore
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

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

    @Ignore
    public void testUpdateModelDeployStatusAndTriggerOnNodesAction_success() throws NoSuchFieldException, IllegalAccessException {
        Field clientField = MLModelManager.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mlModelManager, client);

        doCallRealMethod().when(mlModelManager).updateModel(anyString(), any(ImmutableMap.class), isA(ActionListener.class));

        MLDeployModelNodesResponse MLDeployModelNodesResponse = mock(MLDeployModelNodesResponse.class);
        doAnswer(invocation -> {
            ActionListener<MLDeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(MLDeployModelNodesResponse);
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
        transportDeployModelAction
            .updateModelDeployStatusAndTriggerOnNodesAction(
                modelId,
                "mock_task_id",
                mlModel,
                localNodeId,
                mlTask,
                Arrays.asList(discoveryNode),
                true
            );
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager).updateModel(anyString(), captor.capture(), any());
        Map<String, Object> map = captor.getValue();
        assertNotNull(map.get(MLModel.PLANNING_WORKER_NODES_FIELD));
        assertEquals(1, (((List) map.get(MLModel.PLANNING_WORKER_NODES_FIELD)).size()));
    }

    @Ignore
    public void testUpdateModelDeployStatusAndTriggerOnNodesAction_whenMLTaskManagerThrowException_ListenerOnFailureExecuted() {
        doCallRealMethod().when(mlModelManager).updateModel(anyString(), any(ImmutableMap.class), isA(ActionListener.class));
        transportDeployModelAction
            .updateModelDeployStatusAndTriggerOnNodesAction(modelId, "mock_task_id", mlModel, localNodeId, mlTask, eligibleNodes, false);
        verify(mlTaskManager).updateMLTask(anyString(), anyMap(), anyLong(), anyBoolean());
    }

}
