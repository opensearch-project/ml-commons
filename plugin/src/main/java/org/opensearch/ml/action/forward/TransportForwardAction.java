/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLExceptionUtils.toJsonString;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import com.google.common.collect.ImmutableMap;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Log4j2
public class TransportForwardAction extends HandledTransportAction<ActionRequest, MLForwardResponse> implements ClusterStateListener {
    private final ClusterService clusterService;
    MLTaskManager mlTaskManager;
    Client client;
    final SdkClient sdkClient;
    MLModelManager mlModelManager;
    DiscoveryNodeHelper nodeHelper;

    private final Settings settings;

    private volatile float modelAutoRedeploySuccessRatio;

    private boolean enableAutoReDeployModel;

    private final MLModelAutoReDeployer mlModelAutoReDeployer;

    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportForwardAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Client client,
        SdkClient sdkClient,
        MLModelManager mlModelManager,
        DiscoveryNodeHelper nodeHelper,
        Settings settings,
        ClusterService clusterService,
        MLModelAutoReDeployer mlModelAutoReDeployer,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLForwardAction.NAME, transportService, actionFilters, MLForwardRequest::new);
        this.mlTaskManager = mlTaskManager;
        this.client = client;
        this.sdkClient = sdkClient;
        this.mlModelManager = mlModelManager;
        this.nodeHelper = nodeHelper;
        this.settings = settings;
        this.clusterService = clusterService;
        this.mlModelAutoReDeployer = mlModelAutoReDeployer;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;

        modelAutoRedeploySuccessRatio = ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO.get(settings);
        enableAutoReDeployModel = ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE, it -> enableAutoReDeployModel = it);
        clusterService.addListener(this);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLForwardResponse> listener) {
        MLForwardRequest mlForwardRequest = MLForwardRequest.fromActionRequest(request);
        MLForwardInput forwardInput = mlForwardRequest.getForwardInput();
        String modelId = forwardInput.getModelId();
        String taskId = forwardInput.getTaskId();
        String tenantId = forwardInput.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, listener)) {
            return;
        }
        MLRegisterModelInput registerModelInput = forwardInput.getRegisterModelInput();
        MLTask mlTask = forwardInput.getMlTask();
        String workerNodeId = forwardInput.getWorkerNodeId();
        MLForwardRequestType requestType = forwardInput.getRequestType();

        String error = forwardInput.getError();
        log.debug("receive forward request: {}", forwardInput.getRequestType());
        try {
            switch (requestType) {
                case DEPLOY_MODEL_DONE:
                    onDeployModelDone(taskId, modelId, tenantId, workerNodeId, error);
                    listener.onResponse(new MLForwardResponse("ok", null));
                    break;
                case REGISTER_MODEL:
                    mlModelManager.registerMLModel(registerModelInput, mlTask);
                    listener.onResponse(new MLForwardResponse("ok", null));
                    break;
                default:
                    throw new IllegalArgumentException("unsupported request type");
            }
        } catch (Exception e) {
            logException("Failed to execute forward action " + forwardInput.getRequestType(), e, log);
            listener.onFailure(e);
        }
    }

    /**
     * Handles a worker node's deploy result. Synchronized so that a result and a node-left event, or two results, can never
     * finalize the same task twice.
     */
    synchronized void onDeployModelDone(String taskId, String modelId, String tenantId, String workerNodeId, String error)
        throws IOException {
        Set<String> workNodes = mlTaskManager.getWorkNodes(taskId);
        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
        if (mlTaskCache == null) {
            // The task already finished: its remaining nodes left the cluster, or it timed out. The sync up job still adds a
            // node that deployed the model successfully to the routing table.
            log.info("Received deploy model result from node {} for task {}, which is no longer tracked", workerNodeId, taskId);
            return;
        }
        FunctionName functionName = mlTaskCache.getMlTask().getFunctionName();
        boolean wasPending = workNodes != null && workNodes.remove(workerNodeId);
        if (error != null) {
            if (wasPending) {
                mlTaskManager.addNodeError(taskId, workerNodeId, error);
            } else {
                // A node the task stopped waiting for, because it left the cluster, replied after all (it was only
                // partitioned, or rejoined). It is no longer part of the task's expected node count, so counting its error
                // could mark the task FAILED although other nodes deployed the model.
                log.info("Ignoring deploy model error for task {} from node {}, which the task no longer waits for", taskId, workerNodeId);
            }
        } else {
            mlModelManager.addModelWorkerNode(modelId, workerNodeId);
            syncModelWorkerNodes(modelId, functionName);
        }
        if (workNodes != null && !workNodes.isEmpty()) {
            Set<String> allNodesInCluster = new HashSet<>(List.of(getAllNodes(clusterService)));
            Set<String> workNodesRemovedFromCluster = workNodes
                .stream()
                .filter(node -> !allNodesInCluster.contains(node))
                .collect(Collectors.toSet());
            removeWorkerNodesFromTask(modelId, mlTaskCache, workNodes, workNodesRemovedFromCluster);
        }
        completeTaskIfNoNodeLeft(taskId, modelId, tenantId, mlTaskCache, workNodes);
    }

    /**
     * A deploy task used to finish only when a worker node replied, and nodes that had left the cluster were dropped only
     * then. When the last nodes a task waits for leave before replying (blue/green, node replacement), no reply ever
     * arrives and the task stays RUNNING with the model DEPLOYING. Drop such nodes as soon as they leave instead.
     */
    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        if (!event.nodesDelta().removed()) {
            return;
        }
        Set<String> removedNodeIds = event.nodesDelta().removedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toSet());
        // the applied cluster state must not be read on the cluster applier thread, and finishing a task reads it
        client.threadPool().executor(GENERAL_THREAD_POOL).execute(() -> {
            try {
                onNodesLeft(removedNodeIds);
            } catch (Exception e) {
                log.error("Failed to update deploy model tasks after nodes {} left the cluster", removedNodeIds, e);
            }
        });
    }

    synchronized void onNodesLeft(Set<String> removedNodeIds) {
        String[] taskIds = mlTaskManager.getAllTaskIds();
        if (taskIds == null) {
            return;
        }
        for (String taskId : taskIds) {
            MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
            Set<String> workNodes = mlTaskManager.getWorkNodes(taskId);
            if (mlTaskCache == null || workNodes == null || mlTaskCache.getMlTask().getTaskType() != MLTaskType.DEPLOY_MODEL) {
                continue;
            }
            Set<String> leftNodes = workNodes.stream().filter(removedNodeIds::contains).collect(Collectors.toSet());
            if (leftNodes.isEmpty()) {
                continue;
            }
            MLTask mlTask = mlTaskCache.getMlTask();
            log.info("Nodes {} left the cluster before reporting the result of deploy model task {}", leftNodes, taskId);
            try {
                removeWorkerNodesFromTask(mlTask.getModelId(), mlTaskCache, workNodes, leftNodes);
                completeTaskIfNoNodeLeft(taskId, mlTask.getModelId(), mlTask.getTenantId(), mlTaskCache, workNodes);
            } catch (Exception e) {
                log.error("Failed to update deploy model task {} after nodes {} left the cluster", taskId, leftNodes, e);
            }
        }
    }

    private void removeWorkerNodesFromTask(String modelId, MLTaskCache mlTaskCache, Set<String> workNodes, Set<String> nodes) {
        if (nodes.isEmpty()) {
            return;
        }
        // drop them from the nodes the task waits for, and from the expected node count used for the success ratio
        workNodes.removeAll(nodes);
        mlTaskCache.updateWorkerNode(nodes);
        mlModelManager.removeModelWorkerNode(modelId, false, nodes.toArray(new String[0]));
    }

    private void completeTaskIfNoNodeLeft(String taskId, String modelId, String tenantId, MLTaskCache mlTaskCache, Set<String> workNodes)
        throws IOException {
        if (workNodes != null && !workNodes.isEmpty()) {
            return;
        }
        if (mlTaskManager.getMLTaskCache(taskId) != mlTaskCache) {
            // already completed by a concurrent result or node-left event
            return;
        }
        FunctionName functionName = mlTaskCache.getMlTask().getFunctionName();
        int currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize();
        MLTaskState taskState = mlTaskCache.hasError() ? MLTaskState.COMPLETED_WITH_ERROR : MLTaskState.COMPLETED;
        if (mlTaskCache.allNodeFailed() || mlTaskCache.getWorkerNodeSize() == 0) {
            taskState = MLTaskState.FAILED;
            currentWorkerNodeCount = 0;
        } else {
            syncModelWorkerNodes(modelId, functionName);
        }
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder.put(MLTask.STATE_FIELD, taskState);
        if (mlTaskCache.hasError()) {
            currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize() - mlTaskCache.getErrors().size();
            builder.put(MLTask.ERROR_FIELD, toJsonString(mlTaskCache.getErrors()));
        }
        boolean clearAutoReDeployRetryTimes = triggerNextModelDeployAndCheckIfRestRetryTimes(workNodes, taskId);
        mlTaskManager.updateMLTask(taskId, tenantId, builder.build(), TASK_SEMAPHORE_TIMEOUT, true);

        MLModelState modelState;
        if (mlTaskCache.allNodeFailed() || mlTaskCache.getWorkerNodeSize() == 0) {
            modelState = MLModelState.DEPLOY_FAILED;
            log.error("deploy model failed on all nodes, model id: {}", modelId);
        } else {
            modelState = mlTaskCache.hasError() ? MLModelState.PARTIALLY_DEPLOYED : MLModelState.DEPLOYED;
        }
        Map<String, Object> updateFields = new HashMap<>();
        updateFields.put(MLModel.MODEL_STATE_FIELD, modelState);
        updateFields.put(MLModel.LAST_DEPLOYED_TIME_FIELD, Instant.now().toEpochMilli());
        updateFields.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, currentWorkerNodeCount);
        if (clearAutoReDeployRetryTimes) {
            log.debug("Model successfully deployed in cluster, setting the auto retry times to 0");
            updateFields.put(MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD, 0);
        }
        log.info("deploy model done with state: {}, model id: {}", modelState, modelId);
        ActionListener updateModelListener = ActionListener.<UpdateResponse>wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML model successfully: {}, model id: {}", response.status(), modelId);
            } else {
                log.error("Failed to update ML model {}, status: {}", modelId, response.status());
            }
        }, e -> { log.error("Failed to update ML model: {}", modelId, e); });
        mlModelManager.updateModel(modelId, tenantId, updateFields, ActionListener.runBefore(updateModelListener, () -> {
            mlModelManager.removeAutoDeployModel(modelId);
        }));
    }

    private boolean triggerNextModelDeployAndCheckIfRestRetryTimes(Set<String> workNodes, String taskId) {
        if (enableAutoReDeployModel && workNodes != null && mlTaskManager.getMLTaskCache(taskId) != null) {
            MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
            int expectedWorkerNodeCount = mlTaskCache.getWorkerNodeSize();
            int receivedWorkerNodesCount = expectedWorkerNodeCount - workNodes.size();
            int successWorkerNodesCount = receivedWorkerNodesCount - mlTaskCache.errorNodesCount();
            if ((float) successWorkerNodesCount / expectedWorkerNodeCount >= modelAutoRedeploySuccessRatio) {
                // Trigger next model auto redeploy.
                mlModelAutoReDeployer.redeployAModel();
                // clear the auto reload retry time by setting the times value to 0.
                return true;
            }
        }
        // Failure case or auto redeploy is not enable case, return false, do not update the corresponding field in the index.
        return false;
    }

    private void syncModelWorkerNodes(String modelId, FunctionName functionName) {
        DiscoveryNode[] allNodes = nodeHelper.getAllNodes();
        String[] workerNodes = mlModelManager.getWorkerNodes(modelId, functionName);
        if (allNodes.length > 1 && workerNodes != null && workerNodes.length > 0) {
            log.debug("Sync to other nodes about worker nodes of model {}: {}", modelId, Arrays.toString(workerNodes));
            MLSyncUpInput syncUpInput = MLSyncUpInput.builder().addedWorkerNodes(ImmutableMap.of(modelId, workerNodes)).build();
            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
            client
                .execute(
                    MLSyncUpAction.INSTANCE,
                    syncUpRequest,
                    ActionListener.wrap(r -> log.debug("Sync up successfully"), e -> log.error("Failed to sync up", e))
                );
        }
    }
}
