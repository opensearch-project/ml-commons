/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO;
import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLExceptionUtils.toJsonString;

import java.time.Instant;
import java.util.*;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.model.MLModelState;
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
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Log4j2
public class TransportForwardAction extends HandledTransportAction<ActionRequest, MLForwardResponse> {
    private final ClusterService clusterService;
    MLTaskManager mlTaskManager;
    Client client;
    MLModelManager mlModelManager;
    DiscoveryNodeHelper nodeHelper;

    private final Settings settings;

    private volatile float modelAutoRedeploySuccessRatio;

    private boolean enableAutoReDeployModel;

    private final MLModelAutoReDeployer mlModelAutoReDeployer;

    @Inject
    public TransportForwardAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Client client,
        MLModelManager mlModelManager,
        DiscoveryNodeHelper nodeHelper,
        Settings settings,
        ClusterService clusterService,
        MLModelAutoReDeployer mlModelAutoReDeployer
    ) {
        super(MLForwardAction.NAME, transportService, actionFilters, MLForwardRequest::new);
        this.mlTaskManager = mlTaskManager;
        this.client = client;
        this.mlModelManager = mlModelManager;
        this.nodeHelper = nodeHelper;
        this.settings = settings;
        this.clusterService = clusterService;
        this.mlModelAutoReDeployer = mlModelAutoReDeployer;

        modelAutoRedeploySuccessRatio = ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO.get(settings);
        enableAutoReDeployModel = ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE.get(settings);

        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE, it -> enableAutoReDeployModel = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLForwardResponse> listener) {
        MLForwardRequest mlForwardRequest = MLForwardRequest.fromActionRequest(request);
        MLForwardInput forwardInput = mlForwardRequest.getForwardInput();
        String modelId = forwardInput.getModelId();
        String taskId = forwardInput.getTaskId();
        MLRegisterModelInput registerModelInput = forwardInput.getRegisterModelInput();
        MLTask mlTask = forwardInput.getMlTask();
        String workerNodeId = forwardInput.getWorkerNodeId();
        MLForwardRequestType requestType = forwardInput.getRequestType();

        String error = forwardInput.getError();
        log.debug("receive forward request: {}", forwardInput.getRequestType());
        try {
            switch (requestType) {
                case DEPLOY_MODEL_DONE:
                    Set<String> workNodes = mlTaskManager.getWorkNodes(taskId);
                    MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
                    FunctionName functionName = mlTaskCache.getMlTask().getFunctionName();
                    if (workNodes != null) {
                        workNodes.remove(workerNodeId);
                    }
                    if (error != null) {
                        mlTaskManager.addNodeError(taskId, workerNodeId, error);
                    } else {
                        mlModelManager.addModelWorkerNode(modelId, workerNodeId);
                        syncModelWorkerNodes(modelId, functionName);
                    }

                    if (workNodes == null || workNodes.size() == 0) {
                        int currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize();
                        MLTaskState taskState = mlTaskCache.hasError() ? MLTaskState.COMPLETED_WITH_ERROR : MLTaskState.COMPLETED;
                        if (mlTaskCache.allNodeFailed()) {
                            taskState = MLTaskState.FAILED;
                            currentWorkerNodeCount = 0;
                        } else {
                            syncModelWorkerNodes(modelId, functionName);
                        }
                        final Map<String, Object> map = new HashMap<>();
                        map.put(MLTask.STATE_FIELD, taskState);
                        if (mlTaskCache.hasError()) {
                            currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize() - mlTaskCache.getErrors().size();
                            map.put(MLTask.ERROR_FIELD, toJsonString(mlTaskCache.getErrors()));
                        }
                        boolean clearAutoReDeployRetryTimes = triggerNextModelDeployAndCheckIfRestRetryTimes(workNodes, taskId);
                        mlTaskManager.updateMLTask(taskId, Collections.unmodifiableMap(map), TASK_SEMAPHORE_TIMEOUT, true);

                        MLModelState modelState;
                        if (!mlTaskCache.allNodeFailed()) {
                            modelState = mlTaskCache.hasError() ? MLModelState.PARTIALLY_DEPLOYED : MLModelState.DEPLOYED;
                        } else {
                            modelState = MLModelState.DEPLOY_FAILED;
                            log.error("deploy model failed on all nodes, model id: {}", modelId);
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
                        mlModelManager.updateModel(modelId, updateFields);
                    }
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
            MLSyncUpInput syncUpInput = MLSyncUpInput.builder().addedWorkerNodes(Map.of(modelId, workerNodes)).build();
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
