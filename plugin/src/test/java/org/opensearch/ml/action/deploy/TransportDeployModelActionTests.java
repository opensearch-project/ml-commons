/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.deploy;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.utils.MLExceptionUtils.LOCAL_MODEL_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.MLExceptionUtils.REMOTE_INFERENCE_DISABLED_ERR_MSG;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
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
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionType;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.deploy.MLDeployModelNodesResponse;
import org.opensearch.ml.common.transport.deploy.MLDeployModelRequest;
import org.opensearch.ml.common.transport.deploy.MLDeployModelResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.encryptor.EncryptorImpl;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.impl.SdkClientFactory;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

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
    SdkClient sdkClient;

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
    private Encryptor encryptor;
    private ModelHelper modelHelper;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private final List<DiscoveryNode> eligibleNodes = mock(List.class);

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), true).build();
        sdkClient = SdkClientFactory.createSdkClient(client, NamedXContentRegistry.EMPTY, Collections.emptyMap());
        clusterSettings = new ClusterSettings(settings, new HashSet<>(Arrays.asList(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN)));
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        when(clusterService.getSettings()).thenReturn(settings);

        encryptor = new EncryptorImpl(null, "m+dWmfmnNRiNlOdej/QelEkvMTyH//frS2TBeS2BP4w=");
        mlEngine = new MLEngine(Path.of("/tmp/test" + randomAlphaOfLength(10)), encryptor);
        modelHelper = new ModelHelper(mlEngine);
        when(mlDeployModelRequest.getModelId()).thenReturn("mockModelId");
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1" });
        DiscoveryNode discoveryNode = mock(DiscoveryNode.class);
        DiscoveryNode[] discoveryNodes = new DiscoveryNode[] { discoveryNode };
        when(nodeFilter.getEligibleNodes(any())).thenReturn(discoveryNodes);
        when(discoveryNode.getId()).thenReturn("node1");

        when(clusterService.localNode()).thenReturn(discoveryNode);
        when(client.threadPool()).thenReturn(threadPool);

        ThreadContext threadContext = new ThreadContext(Settings.EMPTY);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        executorService = mock(ExecutorService.class);
        when(threadPool.executor(anyString())).thenReturn(executorService);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        when(mlDeployModelRequest.isUserInitiatedDeployRequest()).thenReturn(true);

        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(true);

        MLStat mlStat = mock(MLStat.class);
        when(mlStats.getStat(eq(MLNodeLevelStat.ML_REQUEST_COUNT))).thenReturn(mlStat);
        transportDeployModelAction = new TransportDeployModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            clusterService,
            threadPool,
            client,
            sdkClient,
            namedXContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            mlStats,
            settings,
            modelAccessControlHelper,
            mlFeatureEnabledSetting
        );
    }

    public void testDoExecute_success() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

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

    public void testDoExecute_success_not_userInitiatedRequest() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        when(mlDeployModelRequest.isUserInitiatedDeployRequest()).thenReturn(false);

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

    public void testDoExecute_success_hidden_model() {
        transportDeployModelAction = spy(
            new TransportDeployModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                sdkClient,
                namedXContentRegistry,
                nodeFilter,
                mlTaskDispatcher,
                mlModelManager,
                mlStats,
                settings,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getIsHidden()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockIndexId");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        doReturn(true).when(transportDeployModelAction).isSuperAdminUserWrapper(clusterService, client);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        verify(deployModelResponseListener).onResponse(any(MLDeployModelResponse.class));
    }

    public void testDoExecute_no_permission_hidden_model() {
        transportDeployModelAction = spy(
            new TransportDeployModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                sdkClient,
                namedXContentRegistry,
                nodeFilter,
                mlTaskDispatcher,
                mlModelManager,
                mlStats,
                settings,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getIsHidden()).thenReturn(true);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("mockIndexId");

        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), Mockito.isA(ActionListener.class));

        doReturn(false).when(transportDeployModelAction).isSuperAdminUserWrapper(clusterService, client);
        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(deployModelResponseListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecute_userHasNoAccessException() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deployModelResponseListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testDoExecuteRemoteInferenceDisabled() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.REMOTE);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        when(mlFeatureEnabledSetting.isRemoteInferenceEnabled()).thenReturn(false);
        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(deployModelResponseListener).onFailure(argumentCaptor.capture());
        assertEquals(REMOTE_INFERENCE_DISABLED_ERR_MSG, argumentCaptor.getValue().getMessage());
    }

    public void testDoExecuteLocalInferenceDisabled() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.TEXT_EMBEDDING);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        when(mlFeatureEnabledSetting.isLocalModelEnabled()).thenReturn(false);
        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(IllegalStateException.class);
        verify(deployModelResponseListener).onFailure(argumentCaptor.capture());
        assertEquals(LOCAL_MODEL_DISABLED_ERR_MSG, argumentCaptor.getValue().getMessage());
    }

    public void test_ValidationFailedException() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(deployModelResponseListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
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
            sdkClient,
            namedXContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            mlStats,
            settings,
            modelAccessControlHelper,
            mlFeatureEnabledSetting
        );

        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, mock(ActionListener.class));
    }

    @Ignore
    public void testDoExecute_whenDeployModelRequestNodeIdsEmpty_thenMLResourceNotFoundException() {
        DiscoveryNodeHelper nodeHelper = mock(DiscoveryNodeHelper.class);
        when(nodeHelper.getEligibleNodes(FunctionName.REMOTE)).thenReturn(new DiscoveryNode[] {});
        TransportDeployModelAction deployModelAction = spy(
            new TransportDeployModelAction(
                transportService,
                actionFilters,
                modelHelper,
                mlTaskManager,
                clusterService,
                threadPool,
                client,
                sdkClient,
                namedXContentRegistry,
                nodeHelper,
                mlTaskDispatcher,
                mlModelManager,
                mlStats,
                settings,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );
        MLDeployModelRequest MLDeployModelRequest1 = mock(MLDeployModelRequest.class);
        when(MLDeployModelRequest1.getModelNodeIds()).thenReturn(null);
        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        deployModelAction.doExecute(mock(Task.class), MLDeployModelRequest1, deployModelResponseListener);
        verify(deployModelResponseListener).onFailure(any(IllegalArgumentException.class));
    }

    public void testDoExecute_whenGetModelHasNPE_exception() {
        doThrow(NullPointerException.class)
            .when(mlModelManager)
            .getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

        ActionListener<MLDeployModelResponse> deployModelResponseListener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(mock(Task.class), mlDeployModelRequest, deployModelResponseListener);
        verify(deployModelResponseListener).onFailure(any(Exception.class));
    }

    public void testDoExecute_whenThreadPoolExecutorException_TaskRemoved() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), Mockito.isA(ActionListener.class));

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
        verify(mlTaskManager).updateMLTask(anyString(), any(), anyMap(), anyLong(), anyBoolean());
    }

    public void testUpdateModelDeployStatusAndTriggerOnNodesAction_success() throws NoSuchFieldException, IllegalAccessException {
        Field clientField = MLModelManager.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(mlModelManager, client);

        doCallRealMethod().when(mlModelManager).updateModel(anyString(), anyString(), any(Map.class), isA(ActionListener.class));

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
                null,
                mlModel,
                localNodeId,
                mlTask,
                List.of(discoveryNode),
                true
            );

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager).updateModel(anyString(), any(), captor.capture(), any());
        Map<String, Object> map = captor.getValue();
        assertNotNull(map.get(MLModel.PLANNING_WORKER_NODES_FIELD));
        assertEquals(1, (((List<?>) map.get(MLModel.PLANNING_WORKER_NODES_FIELD)).size()));
    }

    public void testUpdateModelDeployStatusAndTriggerOnNodesAction_whenMLTaskManagerThrowException_ListenerOnFailureExecuted() {
        doCallRealMethod().when(mlModelManager).updateModel(anyString(), any(), any(Map.class), isA(ActionListener.class));
        transportDeployModelAction
            .updateModelDeployStatusAndTriggerOnNodesAction(
                modelId,
                "mock_task_id",
                null,
                mlModel,
                localNodeId,
                mlTask,
                eligibleNodes,
                false
            );
        verify(mlTaskManager).updateMLTask(anyString(), any(), anyMap(), anyLong(), anyBoolean());
    }

    public void testDeployRemoteModel_success() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getModelId()).thenReturn("test-model-id");
        when(mlModel.getTenantId()).thenReturn("test-tenant");
        when(mlModel.getModelContentHash()).thenReturn("test-hash");
        when(mlModel.getIsHidden()).thenReturn(false);

        MLTask mlTask = mock(MLTask.class);
        when(mlTask.getTaskId()).thenReturn("test-task-id");

        DiscoveryNode node = mock(DiscoveryNode.class);
        when(node.getId()).thenReturn("node1");
        List<DiscoveryNode> nodes = List.of(node);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onResponse(mock(UpdateResponse.class));
            return null;
        }).when(mlModelManager).updateModel(anyString(), anyString(), anyMap(), any());

        doAnswer(invocation -> {
            ActionListener<MLDeployModelNodesResponse> listener = invocation.getArgument(2);
            listener.onResponse(mock(MLDeployModelNodesResponse.class));
            return null;
        }).when(client).execute(any(), any(), any());

        when(mlTaskManager.contains(anyString())).thenReturn(true);

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.deployRemoteModel(mlModel, mlTask, "local-node", nodes, true, listener);

        verify(listener).onResponse(any(MLDeployModelResponse.class));
    }

    public void testDeployRemoteModel_failure() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getModelId()).thenReturn("test-model-id");
        when(mlModel.getTenantId()).thenReturn("test-tenant");
        when(mlModel.getModelContentHash()).thenReturn("test-hash");
        when(mlModel.getIsHidden()).thenReturn(false);

        MLTask mlTask = mock(MLTask.class);
        when(mlTask.getTaskId()).thenReturn("test-task-id");

        DiscoveryNode node = mock(DiscoveryNode.class);
        when(node.getId()).thenReturn("node1");
        List<DiscoveryNode> nodes = List.of(node);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> listener = invocation.getArgument(3);
            listener.onFailure(new RuntimeException("Update failed"));
            return null;
        }).when(mlModelManager).updateModel(anyString(), anyString(), anyMap(), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.deployRemoteModel(mlModel, mlTask, "local-node", nodes, true, listener);

        verify(listener).onFailure(any(RuntimeException.class));
    }

    public void testDoExecute_deployToAllNodes_false() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        // Use the existing mlDeployModelRequest but override specific nodes
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1", "node2" });

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Set up multiple eligible nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("node1");
        when(node2.getId()).thenReturn("node2");
        DiscoveryNode[] nodes = { node1, node2 };
        when(nodeFilter.getEligibleNodes(any())).thenReturn(nodes);
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(null);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("task-id");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onResponse(any(MLDeployModelResponse.class));
    }

    public void testDoExecute_workerNodesConflict() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        // Use the existing mlDeployModelRequest but override specific nodes
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1" });

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Set up eligible nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("node1");
        when(node2.getId()).thenReturn("node2");
        DiscoveryNode[] nodes = { node1, node2 };
        when(nodeFilter.getEligibleNodes(any())).thenReturn(nodes);

        // Set up worker nodes conflict - model is already deployed on node2 but target is node1
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(new String[] { "node2" });

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    public void testDoExecute_noEligibleNodes() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        // Use the existing mlDeployModelRequest but override to request non-existent node
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "non-existent-node" });

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Set up eligible nodes that don't match the requested nodes
        DiscoveryNode existingNode = mock(DiscoveryNode.class);
        when(existingNode.getId()).thenReturn("existing-node");
        DiscoveryNode[] nodes = { existingNode };
        when(nodeFilter.getEligibleNodes(any())).thenReturn(nodes);

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    public void testDoExecute_deployToAllNodes_true() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        // Use null or empty array to trigger deployToAllNodes = true
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(null);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Set up eligible nodes
        DiscoveryNode node1 = mock(DiscoveryNode.class);
        DiscoveryNode node2 = mock(DiscoveryNode.class);
        when(node1.getId()).thenReturn("node1");
        when(node2.getId()).thenReturn("node2");
        DiscoveryNode[] nodes = { node1, node2 };
        when(nodeFilter.getEligibleNodes(any())).thenReturn(nodes);

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("task-id");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onResponse(any(MLDeployModelResponse.class));
    }

    public void testDoExecute_accessControlFailure() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Mock access control to return false (no access)
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), anyString(), any(), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onFailure(any(OpenSearchStatusException.class));
    }

    public void testDoExecute_hiddenModelNonSuperAdmin() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(true);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        // Mock the isSuperAdminUserWrapper to return false (not super admin)
        TransportDeployModelAction spyAction = spy(transportDeployModelAction);
        doReturn(false).when(spyAction).isSuperAdminUserWrapper(any(), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        spyAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onFailure(any(OpenSearchStatusException.class));
    }

    public void testDoExecute_taskManagerNotContains() {
        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        IndexResponse indexResponse = mock(IndexResponse.class);
        when(indexResponse.getId()).thenReturn("task-id");
        doAnswer(invocation -> {
            ActionListener<IndexResponse> listener = invocation.getArgument(1);
            listener.onResponse(indexResponse);
            return null;
        }).when(mlTaskManager).createMLTask(any(MLTask.class), any());

        // Mock mlTaskManager.contains to return false
        when(mlTaskManager.contains(anyString())).thenReturn(false);

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        transportDeployModelAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onResponse(any(MLDeployModelResponse.class));
    }

    public void testDoExecute_customDeploymentNotAllowed() {
        // Override the settings to disable custom deployment plan
        Settings restrictiveSettings = Settings.builder().put(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN.getKey(), false).build();
        ClusterSettings restrictiveClusterSettings = new ClusterSettings(
            restrictiveSettings,
            new HashSet<>(Arrays.asList(ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN))
        );
        when(clusterService.getClusterSettings()).thenReturn(restrictiveClusterSettings);
        when(clusterService.getSettings()).thenReturn(restrictiveSettings);

        // Create a new instance with restrictive settings
        TransportDeployModelAction restrictiveAction = new TransportDeployModelAction(
            transportService,
            actionFilters,
            modelHelper,
            mlTaskManager,
            clusterService,
            threadPool,
            client,
            sdkClient,
            namedXContentRegistry,
            nodeFilter,
            mlTaskDispatcher,
            mlModelManager,
            mlStats,
            restrictiveSettings,
            modelAccessControlHelper,
            mlFeatureEnabledSetting
        );

        MLModel mlModel = mock(MLModel.class);
        when(mlModel.getAlgorithm()).thenReturn(FunctionName.ANOMALY_LOCALIZATION);
        when(mlModel.getModelGroupId()).thenReturn("test-group-id");
        when(mlModel.getIsHidden()).thenReturn(false);

        // Set specific nodes (not deploy to all)
        when(mlDeployModelRequest.getModelNodeIds()).thenReturn(new String[] { "node1" });

        doAnswer(invocation -> {
            ActionListener<MLModel> listener = invocation.getArgument(4);
            listener.onResponse(mlModel);
            return null;
        }).when(mlModelManager).getModel(anyString(), any(), isNull(), any(String[].class), any());

        ActionListener<MLDeployModelResponse> listener = mock(ActionListener.class);
        restrictiveAction.doExecute(null, mlDeployModelRequest, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

}
