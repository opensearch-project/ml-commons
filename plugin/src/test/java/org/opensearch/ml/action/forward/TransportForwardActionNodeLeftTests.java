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
import static org.mockito.Mockito.doAnswer;
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
import org.opensearch.cluster.ClusterChangedEvent;
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
 * Deploy task completion when worker nodes leave the cluster before replying (blue/green, node replacement), and the
 * effects of completion on the auto redeploy chain and retry counter. Uses a real {@link MLTaskManager} task cache.
 */
public class TransportForwardActionNodeLeftTests extends OpenSearchTestCase {

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
        // node-left handling runs on the general thread pool; run it inline
        ExecutorService direct = mock(ExecutorService.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(direct).execute(any(Runnable.class));
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.executor(anyString())).thenReturn(direct);
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

    private void nodesLeave(ClusterState before, ClusterState after) {
        forwardAction.clusterChanged(new ClusterChangedEvent("test", after, before));
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

    public void testLastPendingNodeLeaves_taskCompletesWithRemainingNodes() {
        clusterHas("n1", "n2", "old1");
        startDeployTask("n1", "n2", "old1");
        reply("n1", null);
        reply("n2", null);
        assertTrue("still waiting for old1", mlTaskManager.contains(TASK_ID));

        clusterHas("n1", "n2");
        nodesLeave(stateWith("n1", "n2", "old1"), stateWith("n1", "n2"));

        assertFalse(mlTaskManager.contains(TASK_ID));
        assertEquals(MLTaskState.COMPLETED, completedTaskFields().get(MLTask.STATE_FIELD));
        Map<String, Object> model = modelUpdateFields();
        assertEquals(MLModelState.DEPLOYED, model.get(MLModel.MODEL_STATE_FIELD));
        assertEquals(2, model.get(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD));
        assertEquals("2 of 2 remaining nodes succeeded: healthy", 0, model.get(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD));
        verify(mlModelManager).removeModelWorkerNode(eq(MODEL_ID), eq(false), eq("old1"));
    }

    public void testAllPendingNodesLeave_taskFails() {
        clusterHas("old1", "old2");
        startDeployTask("old1", "old2");

        clusterHas("n1");
        nodesLeave(stateWith("old1", "old2", "n1"), stateWith("n1"));

        assertFalse(mlTaskManager.contains(TASK_ID));
        assertEquals(MLTaskState.FAILED, completedTaskFields().get(MLTask.STATE_FIELD));
        assertEquals(MLModelState.DEPLOY_FAILED, modelUpdateFields().get(MLModel.MODEL_STATE_FIELD));
    }

    public void testUnrelatedNodeLeaves_taskKeepsWaiting() {
        clusterHas("n1", "n2", "other");
        startDeployTask("n1", "n2");
        nodesLeave(stateWith("n1", "n2", "other"), stateWith("n1", "n2"));

        assertTrue(mlTaskManager.contains(TASK_ID));
        verify(mlTaskManager, never()).updateMLTask(anyString(), any(), any(Map.class), anyLong(), eq(true));
    }

    public void testLateReplyAfterNodeLeftCompletion_isIgnored() {
        clusterHas("n1", "old1");
        startDeployTask("n1", "old1");
        reply("n1", null);
        clusterHas("n1");
        nodesLeave(stateWith("n1", "old1"), stateWith("n1"));
        assertFalse(mlTaskManager.contains(TASK_ID));

        // the departed node's reply arrives anyway (e.g. it was only partitioned)
        assertEquals("ok", reply("old1", null).getStatus());
        verify(mlTaskManager, times(1)).updateMLTask(eq(TASK_ID), any(), any(Map.class), anyLong(), eq(true));
        verify(mlModelManager, times(1)).updateModel(eq(MODEL_ID), any(), any(Map.class), any(ActionListener.class));
    }

    public void testLateErrorFromDepartedNode_doesNotFailTask() {
        clusterHas("n1", "n2", "old1");
        startDeployTask("n1", "n2", "old1");
        reply("n1", null);
        clusterHas("n1", "n2");
        nodesLeave(stateWith("n1", "n2", "old1"), stateWith("n1", "n2"));
        // old1 was only partitioned: its failure arrives after the task stopped waiting for it
        reply("old1", "deploy failed on old1");
        reply("n2", null);

        assertEquals(MLTaskState.COMPLETED, completedTaskFields().get(MLTask.STATE_FIELD));
        Map<String, Object> model = modelUpdateFields();
        assertEquals(MLModelState.DEPLOYED, model.get(MLModel.MODEL_STATE_FIELD));
        assertEquals(2, model.get(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD));
    }

    public void testNodeRemovedFromClusterBeforeLastReply_notCountedAsWorker() {
        clusterHas("n1", "n2", "old1");
        startDeployTask("n1", "n2", "old1");
        // old1 leaves; the next reply drops it from the task (before this fix the expected count kept it)
        clusterHas("n1", "n2");
        reply("n1", null);
        reply("n2", null);

        assertEquals(MLTaskState.COMPLETED, completedTaskFields().get(MLTask.STATE_FIELD));
        assertEquals(2, modelUpdateFields().get(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD));
    }

    public void testClusterChangedWithoutRemovedNodes_doesNothing() {
        clusterHas("n1");
        startDeployTask("n1");
        forwardAction.clusterChanged(new ClusterChangedEvent("test", stateWith("n1", "n2"), stateWith("n1")));
        verify(threadPool, never()).executor(anyString());
        assertTrue(mlTaskManager.contains(TASK_ID));
    }

    public void testConcurrentRepliesAndNodeLeft_completeTaskExactlyOnce() throws Exception {
        for (int round = 0; round < 30; round++) {
            org.mockito.Mockito.clearInvocations(mlTaskManager, mlModelManager);
            String[] workers = new String[8];
            for (int i = 0; i < workers.length; i++) {
                workers[i] = "n" + i;
            }
            clusterHas(workers);
            startDeployTask(workers);
            ClusterState before = stateWith(workers);
            ClusterState after = stateWith("n0", "n1", "n2", "n3");
            java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(workers.length + 1);
            List<Thread> threads = new java.util.ArrayList<>();
            for (String w : workers) {
                threads.add(new Thread(() -> {
                    try {
                        start.await();
                        MLForwardInput input = MLForwardInput
                            .builder()
                            .requestType(DEPLOY_MODEL_DONE)
                            .taskId(TASK_ID)
                            .modelId(MODEL_ID)
                            .workerNodeId(w)
                            .build();
                        forwardAction.doExecute(task, MLForwardRequest.builder().forwardInput(input).build(), mock(ActionListener.class));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
            }
            threads.add(new Thread(() -> {
                try {
                    start.await();
                    nodesLeave(before, after);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
            threads.forEach(Thread::start);
            for (Thread t : threads) {
                t.join(10_000);
            }
            assertFalse(mlTaskManager.contains(TASK_ID));
            verify(mlTaskManager, times(1)).updateMLTask(eq(TASK_ID), any(), any(Map.class), anyLong(), eq(true));
            verify(mlModelManager, times(1)).updateModel(eq(MODEL_ID), any(), any(Map.class), any(ActionListener.class));
        }
    }
}
