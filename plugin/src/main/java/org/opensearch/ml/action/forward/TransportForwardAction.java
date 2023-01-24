/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import static org.opensearch.ml.task.MLTaskManager.TASK_SEMAPHORE_TIMEOUT;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;
import static org.opensearch.ml.utils.MLExceptionUtils.toJsonString;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Log4j2
public class TransportForwardAction extends HandledTransportAction<ActionRequest, MLForwardResponse> {
    MLTaskManager mlTaskManager;
    Client client;
    MLModelManager mlModelManager;
    DiscoveryNodeHelper nodeHelper;

    @Inject
    public TransportForwardAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLTaskManager mlTaskManager,
        Client client,
        MLModelManager mlModelManager,
        DiscoveryNodeHelper nodeHelper
    ) {
        super(MLForwardAction.NAME, transportService, actionFilters, MLForwardRequest::new);
        this.mlTaskManager = mlTaskManager;
        this.client = client;
        this.mlModelManager = mlModelManager;
        this.nodeHelper = nodeHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLForwardResponse> listener) {
        MLForwardRequest mlForwardRequest = MLForwardRequest.fromActionRequest(request);
        MLForwardInput forwardInput = mlForwardRequest.getForwardInput();
        String modelId = forwardInput.getModelId();
        String taskId = forwardInput.getTaskId();
        MLUploadInput uploadInput = forwardInput.getUploadInput();
        MLTask mlTask = forwardInput.getMlTask();
        String workerNodeId = forwardInput.getWorkerNodeId();
        MLForwardRequestType requestType = forwardInput.getRequestType();

        String error = forwardInput.getError();
        log.debug("receive forward request: {}", forwardInput.getRequestType());
        try {
            switch (requestType) {
                case LOAD_MODEL_DONE:
                    Set<String> workNodes = mlTaskManager.getWorkNodes(taskId);
                    if (workNodes != null) {
                        workNodes.remove(workerNodeId);
                    }

                    if (error != null) {
                        mlTaskManager.addNodeError(taskId, workerNodeId, error);
                    } else {
                        mlModelManager.addModelWorkerNode(modelId, workerNodeId);
                        syncModelWorkerNodes(modelId);
                    }

                    if (workNodes == null || workNodes.size() == 0) {
                        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
                        int currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize();
                        MLTaskState taskState = mlTaskCache.hasError() ? MLTaskState.COMPLETED_WITH_ERROR : MLTaskState.COMPLETED;
                        if (mlTaskCache.allNodeFailed()) {
                            taskState = MLTaskState.FAILED;
                            currentWorkerNodeCount = 0;
                        } else {
                            syncModelWorkerNodes(modelId);
                        }
                        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
                        builder.put(MLTask.STATE_FIELD, taskState);
                        if (mlTaskCache.hasError()) {
                            currentWorkerNodeCount = mlTaskCache.getWorkerNodeSize() - mlTaskCache.getErrors().size();
                            builder.put(MLTask.ERROR_FIELD, toJsonString(mlTaskCache.getErrors()));
                        }
                        mlTaskManager.updateMLTask(taskId, builder.build(), TASK_SEMAPHORE_TIMEOUT, true);

                        MLModelState modelState;
                        if (!mlTaskCache.allNodeFailed()) {
                            modelState = mlTaskCache.hasError() ? MLModelState.PARTIALLY_LOADED : MLModelState.LOADED;
                        } else {
                            modelState = MLModelState.LOAD_FAILED;
                            log.error("load model failed on all nodes, model id: {}", modelId);
                        }
                        log.info("load model done with state: {}, model id: {}", modelState, modelId);
                        mlModelManager
                            .updateModel(
                                modelId,
                                ImmutableMap
                                    .of(
                                        MLModel.MODEL_STATE_FIELD,
                                        modelState,
                                        MLModel.LAST_LOADED_TIME_FIELD,
                                        Instant.now().toEpochMilli(),
                                        MLModel.CURRENT_WORKER_NODE_COUNT_FIELD,
                                        currentWorkerNodeCount
                                    )
                            );
                    }
                    listener.onResponse(new MLForwardResponse("ok", null));
                    break;
                case UPLOAD_MODEL:
                    mlModelManager.uploadMLModel(uploadInput, mlTask);
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

    private void syncModelWorkerNodes(String modelId) {
        DiscoveryNode[] allNodes = nodeHelper.getAllNodes();
        String[] workerNodes = mlModelManager.getWorkerNodes(modelId);
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
