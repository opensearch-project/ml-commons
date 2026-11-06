/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import static java.util.Collections.emptyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO;
import static org.opensearch.ml.common.transport.forward.MLForwardRequestType.DEPLOY_MODEL_DONE;
import static org.opensearch.ml.utils.TestHelper.ML_ROLE;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

/**
 * Effects of deploy task completion on the auto redeploy chain and the model's auto redeploy retry counter. Uses a real
 * {@link MLTaskManager} task cache.
 */
public class TransportForwardActionRedeployChainTests extends OpenSearchTestCase {

    private static final String TASK_ID = "task1";
    private static final String MODEL_ID = "model1";

    @Mock
    private TransportService transportService;
    @Mock
    private ActionFilters actionFilters;
    @Mock
    private Client client;
    @Mock
    private SdkClient sdkClient;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private DiscoveryNodeHelper nodeHelper;
    @Mock
    private ClusterService clusterService;
    @Mock
    private MLModelAutoReDeployer mlModelAutoReDeployer;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private Task task;
    @Mock
    private ThreadPool threadPool;

    private MLTaskManager mlTaskManager;
    private TransportForwardAction forwardAction;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // the task index update after completion is not under test; drop it
        ThreadPool taskManagerThreadPool = mock(ThreadPool.class);
        when(taskManagerThreadPool.executor(anyString())).thenReturn(mock(ExecutorService.class));
        mlTaskManager = spy(new MLTaskManager(client, sdkClient, taskManagerThreadPool, mock(MLIndicesHandler.class)));
        when(nodeHelper.getAllNodes()).thenReturn(new DiscoveryNode[0]);
        forwardAction = newAction(true);
    }

    private TransportForwardAction newAction(boolean autoRedeployEnabled) {
        Settings settings = Settings
            .builder()
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.getKey(), autoRedeployEnabled)
            .put(ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO.getKey(), 0.8)
            .build();
        when(clusterService.getClusterSettings()).thenReturn(clusterSetting(settings, ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE));
        return new TransportForwardAction(
            transportService,
            actionFilters,
            mlTaskManager,
            client,
            sdkClient,
            mlModelManager,
            nodeHelper,
            settings,
            clusterService,
            mlModelAutoReDeployer,
            mlFeatureEnabledSetting
        );
    }

    // one instance per id: a node with the same id but a new ephemeral id is a different (restarted) node
    private final Map<String, DiscoveryNode> nodes = new java.util.HashMap<>();

    private DiscoveryNode node(String id) {
        return nodes
            .computeIfAbsent(id, k -> new DiscoveryNode(k, buildNewFakeTransportAddress(), emptyMap(), Set.of(ML_ROLE), Version.CURRENT));
    }

    private ClusterState stateWith(String... nodeIds) {
        DiscoveryNodes.Builder nodes = DiscoveryNodes.builder();
        for (String id : nodeIds) {
            nodes.add(node(id));
        }
        return ClusterState.builder(new ClusterName("test")).nodes(nodes).build();
    }

    private void clusterHas(String... nodeIds) {
        when(clusterService.state()).thenReturn(stateWith(nodeIds));
    }

    private void startDeployTask(String... workerNodes) {
        MLTask mlTask = MLTask
            .builder()
            .taskId(TASK_ID)
            .modelId(MODEL_ID)
            .taskType(MLTaskType.DEPLOY_MODEL)
            .functionName(FunctionName.TEXT_EMBEDDING)
            .state(MLTaskState.RUNNING)
            .async(true)
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .build();
        mlTaskManager.add(mlTask, List.of(workerNodes));
    }

    private MLForwardResponse reply(String workerNode, String error) {
        MLForwardInput input = MLForwardInput
            .builder()
            .requestType(DEPLOY_MODEL_DONE)
            .taskId(TASK_ID)
            .modelId(MODEL_ID)
            .workerNodeId(workerNode)
            .error(error)
            .build();
        ActionListener<MLForwardResponse> listener = mock(ActionListener.class);
        forwardAction.doExecute(task, MLForwardRequest.builder().forwardInput(input).build(), listener);
        ArgumentCaptor<MLForwardResponse> response = ArgumentCaptor.forClass(MLForwardResponse.class);
        verify(listener).onResponse(response.capture());
        return response.getValue();
    }

    private Map<String, Object> completedTaskFields() {
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mlTaskManager).updateMLTask(eq(TASK_ID), any(), fields.capture(), anyLong(), eq(true));
        return fields.getValue();
    }

    private Map<String, Object> modelUpdateFields() {
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(mlModelManager).updateModel(eq(MODEL_ID), any(), fields.capture(), any(ActionListener.class));
        return fields.getValue();
    }

    public void testBelowSuccessRatio_chainContinuesButRetryCounterKept() {
        clusterHas("n1", "n2");
        startDeployTask("n1", "n2");
        reply("n1", null);
        reply("n2", "Exceed max local model per node limit");

        assertEquals(MLTaskState.COMPLETED_WITH_ERROR, completedTaskFields().get(MLTask.STATE_FIELD));
        Map<String, Object> model = modelUpdateFields();
        assertEquals(MLModelState.PARTIALLY_DEPLOYED, model.get(MLModel.MODEL_STATE_FIELD));
        assertFalse(model.containsKey(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD));
        verify(mlModelAutoReDeployer, times(1)).redeployAModel();
        verify(mlModelAutoReDeployer).onDeployTaskResult(MODEL_ID, TASK_ID, false);
    }

    public void testHealthyDeployWithAutoRedeployDisabled_resetsRetryCounter() {
        forwardAction = newAction(false);
        clusterHas("n1");
        startDeployTask("n1");
        reply("n1", null);

        assertEquals(0, modelUpdateFields().get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD));
        verify(mlModelAutoReDeployer).onDeployTaskResult(MODEL_ID, TASK_ID, true);
        verify(mlModelAutoReDeployer, never()).redeployAModel();
    }
}
