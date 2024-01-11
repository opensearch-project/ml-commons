/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.MLTaskState.FAILED;
import static org.opensearch.ml.common.transport.forward.MLForwardRequestType.DEPLOY_MODEL_DONE;
import static org.opensearch.ml.common.transport.forward.MLForwardRequestType.REGISTER_MODEL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.TransportService;

public class TransportForwardActionTests extends OpenSearchTestCase {

    @Mock
    TransportService transportService;
    @Mock
    ActionFilters actionFilters;
    @Mock
    MLTaskManager mlTaskManager;
    @Mock
    Client client;
    @Mock
    MLModelManager mlModelManager;
    @Mock
    DiscoveryNodeHelper nodeHelper;
    @Mock
    Task task;
    @Mock
    ActionListener<MLForwardResponse> listener;
    MLTaskCache mlTaskCache;

    private TransportForwardAction forwardAction;

    Settings settings = Settings
        .builder()
        .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), true)
        .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO.getKey(), 0.8)
        .build();
    @Mock
    private ClusterService clusterService;
    @Mock
    MLModelAutoReDeployer mlModelAutoReDeployer;

    DiscoveryNode node1;
    DiscoveryNode node2;
    String nodeId1 = "test_node_id1";
    String nodeId2 = "test_node_id2";
    String error = "test_error";
    String taskId = "test_task_id";
    String modelId = "test_model_id";

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        ClusterSettings clusterSettings = clusterSetting(
            settings,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE,
            ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES,
            ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN,
            ML_COMMONS_ONLY_RUN_ON_ML_NODE
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        forwardAction = spy(
            new TransportForwardAction(
                transportService,
                actionFilters,
                mlTaskManager,
                client,
                mlModelManager,
                nodeHelper,
                settings,
                clusterService,
                mlModelAutoReDeployer
            )
        );

        node1 = new DiscoveryNode(nodeId1, buildNewFakeTransportAddress(), emptyMap(), Set.of(ML_ROLE), Version.CURRENT);
        node2 = new DiscoveryNode(nodeId2, buildNewFakeTransportAddress(), emptyMap(), Set.of(ML_ROLE), Version.CURRENT);

        when(nodeHelper.getAllNodes()).thenReturn(new DiscoveryNode[] { node1, node2 });
    }

    public void testDoExecute_DeployModelDone_Error() {
        Set<String> workerNodes = new HashSet<>();
        workerNodes.add(nodeId1);
        workerNodes.add(nodeId2);
        when(mlTaskManager.getWorkNodes(anyString())).thenReturn(workerNodes);
        MLTaskCache mlTaskCache = MLTaskCache
            .builder()
            .mlTask(createMlTask(MLTaskType.REGISTER_MODEL))
            .workerNodes(Arrays.asList(nodeId1, nodeId2))
            .build();
        mlTaskCache.addError(nodeId1, error);
        doReturn(mlTaskCache).when(mlTaskManager).getMLTaskCache(anyString());

        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .error(error)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        assertEquals("ok", response.getValue().getStatus());
        assertNull(response.getValue().getMlOutput());
        verify(mlTaskManager).addNodeError(eq(taskId), eq(nodeId1), eq(error));
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testDoExecute_DeployModelDone_NoError() {
        Set<String> workerNodes = new HashSet<>();
        workerNodes.add(nodeId1);
        workerNodes.add(nodeId2);
        when(mlTaskManager.getWorkNodes(anyString())).thenReturn(workerNodes);
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(new String[] { nodeId1, nodeId2 });
        MLTaskCache mlTaskCache = mock(MLTaskCache.class);
        MLTask mlTask = mock(MLTask.class);
        when(mlTaskCache.getMlTask()).thenReturn(mlTask);
        when(mlTask.getFunctionName()).thenReturn(FunctionName.TEXT_EMBEDDING);
        when(mlTaskManager.getMLTaskCache(anyString())).thenReturn(mlTaskCache);

        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .modelId(modelId)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        assertEquals("ok", response.getValue().getStatus());
        assertNull(response.getValue().getMlOutput());
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testDoExecute_DeployModelDone_successDeploy_ratio_exceed_configuration() {
        Set<String> workerNodes = new HashSet<>();
        workerNodes.add(nodeId1);
        when(mlTaskManager.getWorkNodes(anyString())).thenReturn(workerNodes);
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(new String[] { nodeId1 });
        MLTaskCache mlTaskCache = mock(MLTaskCache.class);
        when(mlTaskCache.getErrors()).thenReturn(Map.of());
        when(mlTaskCache.hasError()).thenReturn(false);
        when(mlTaskCache.getWorkerNodeSize()).thenReturn(1);
        when(mlTaskCache.errorNodesCount()).thenReturn(0);
        MLTask mlTask = mock(MLTask.class);
        when(mlTask.getFunctionName()).thenReturn(FunctionName.REMOTE);
        when(mlTaskCache.getMlTask()).thenReturn(mlTask);
        when(mlTaskManager.getMLTaskCache(anyString())).thenReturn(mlTaskCache);
        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .modelId(modelId)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        assertEquals("ok", response.getValue().getStatus());
        assertNull(response.getValue().getMlOutput());
        verify(mlTaskManager, times(1)).updateMLTask(anyString(), any(), anyLong(), anyBoolean());
    }

    public void testDoExecute_DeployModelDone_Error_NullTaskWorkerNodes() {
        when(mlTaskManager.getWorkNodes(anyString())).thenReturn(null);
        List<String> workerNodes = Arrays.asList(nodeId1, nodeId2);
        MLTaskCache mlTaskCache = MLTaskCache.builder().mlTask(createMlTask(MLTaskType.REGISTER_MODEL)).workerNodes(workerNodes).build();
        mlTaskCache.addError(nodeId1, error);
        doReturn(mlTaskCache).when(mlTaskManager).getMLTaskCache(anyString());
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(new String[] { nodeId1, nodeId2 });

        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .modelId(modelId)
            .error(error)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        assertEquals("ok", response.getValue().getStatus());
        assertNull(response.getValue().getMlOutput());
        ArgumentCaptor<MLSyncUpNodesRequest> syncUpRequest = ArgumentCaptor.forClass(MLSyncUpNodesRequest.class);
        verify(client).execute(eq(MLSyncUpAction.INSTANCE), syncUpRequest.capture(), any());
        assertEquals(modelId, syncUpRequest.getValue().getSyncUpInput().getAddedWorkerNodes().keySet().toArray(new String[0])[0]);
        assertEquals(2, syncUpRequest.getValue().getSyncUpInput().getAddedWorkerNodes().get(modelId).length);
    }

    public void testDoExecute_DeployModelDone_AllFailed() {
        Set<String> workerNodes = new HashSet<>();
        when(mlTaskManager.getWorkNodes(anyString())).thenReturn(workerNodes);
        MLTaskCache mlTaskCache = MLTaskCache
            .builder()
            .mlTask(createMlTask(MLTaskType.REGISTER_MODEL))
            .workerNodes(Arrays.asList(nodeId1))
            .build();
        mlTaskCache.addError(nodeId1, error);
        doReturn(mlTaskCache).when(mlTaskManager).getMLTaskCache(anyString());
        when(mlModelManager.getWorkerNodes(anyString(), any())).thenReturn(new String[] { nodeId1, nodeId2 });

        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .modelId(modelId)
            .error(error)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        verify(client, never()).execute(eq(MLSyncUpAction.INSTANCE), any(), any());
        verify(mlTaskManager).addNodeError(eq(taskId), eq(nodeId1), eq(error));
        ArgumentCaptor<Map<String, Object>> updatedFields = ArgumentCaptor.forClass(Map.class);
        verify(mlTaskManager).updateMLTask(anyString(), updatedFields.capture(), anyLong(), anyBoolean());
        assertEquals(FAILED, (MLTaskState) updatedFields.getValue().get(MLTask.STATE_FIELD));
    }

    public void testDoExecute_DeployModel_Exception() {
        doThrow(new RuntimeException(error)).when(mlTaskManager).getWorkNodes(any());
        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(taskId)
            .modelId(modelId)
            .error(error)
            .workerNodeId(nodeId1)
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<Exception> exception = ArgumentCaptor.forClass(Exception.class);
        verify(listener).onFailure(exception.capture());
        assertEquals(error, exception.getValue().getMessage());
    }

    public void testDoExecute_RegisterModel() {
        MLForwardInput forwardInput = MLForwardInput
            .builder()
            .requestType(REGISTER_MODEL)
            .mlTask(createMlTask(MLTaskType.REGISTER_MODEL))
            .taskId(taskId)
            .registerModelInput(prepareInput())
            .build();
        MLForwardRequest forwardRequest = MLForwardRequest.builder().forwardInput(forwardInput).build();
        forwardAction.doExecute(task, forwardRequest, listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        assertEquals("ok", response.getValue().getStatus());
        assertNull(response.getValue().getMlOutput());
        verify(mlModelManager).registerMLModel(any(), any());
    }

    private MLTask createMlTask(MLTaskType mlTaskType) {
        return MLTask
            .builder()
            .async(true)
            .taskId(taskId)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .state(MLTaskState.RUNNING)
            .taskType(mlTaskType)
            .build();
    }

    private MLRegisterModelInput prepareInput() {
        MLRegisterModelInput registerModelInput = MLRegisterModelInput
            .builder()
            .functionName(FunctionName.BATCH_RCF)
            .deployModel(true)
            .version("1.0")
            .modelGroupId("model group id")
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
            .url("http://test_url")
            .build();
        return registerModelInput;
    }
}
