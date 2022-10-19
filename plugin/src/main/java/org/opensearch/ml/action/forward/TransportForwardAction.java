/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.forward;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.NamedXContentRegistry;
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
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@Log4j2
public class TransportForwardAction extends HandledTransportAction<ActionRequest, MLForwardResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    MLTaskDispatcher mlTaskDispatcher;
    MLIndicesHandler mlIndicesHandler;
    MLModelManager mlModelManager;
    private DiscoveryNodeHelper nodeFilter;

    @Inject
    public TransportForwardAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLTaskDispatcher mlTaskDispatcher,
        MLIndicesHandler mlIndicesHandler,
        MLModelManager mlModelManager,
        DiscoveryNodeHelper nodeFilter
    ) {
        super(MLForwardAction.NAME, transportService, actionFilters, MLForwardRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlModelManager = mlModelManager;
        this.nodeFilter = nodeFilter;
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
        log.debug("receive forward request: " + forwardInput.getRequestType());
        try {
            switch (requestType) {
                case LOAD_MODEL_DONE:
                    List<String> workNodes = mlTaskManager.getWorkNodes(taskId);
                    if (workNodes != null) {
                        workNodes.remove(workerNodeId);
                    }

                    if (error != null) {
                        mlTaskManager.addNodeError(taskId, workerNodeId, error);
                    } else {
                        mlModelManager.addModelWorkerNode(modelId, workerNodeId);
                    }

                    if (workNodes == null || workNodes.size() == 0) {
                        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
                        MLTaskState taskState = mlTaskCache.hasError() ? MLTaskState.COMPLETED_WITH_ERROR : MLTaskState.COMPLETED;
                        if (mlTaskCache.allNodeFailed()) {
                            taskState = MLTaskState.FAILED;
                        } else {
                            DiscoveryNode[] allNodes = nodeFilter.getAllNodes();
                            String[] workerNodes = mlModelManager.getWorkerNodes(modelId);
                            if (allNodes.length > 1 && workerNodes.length > 0) {
                                log.debug("sync model routing to other nodes. model loaded on nodes: {}", Arrays.toString(workerNodes));
                                MLSyncUpInput syncUpInput = MLSyncUpInput
                                    .builder()
                                    .addedWorkerNodes(ImmutableMap.of(modelId, workerNodes))
                                    .build();
                                MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
                                client
                                    .execute(
                                        MLSyncUpAction.INSTANCE,
                                        syncUpRequest,
                                        ActionListener
                                            .wrap(r -> { log.debug("Sync up successfully"); }, e -> { log.error("Failed to sync up", e); })
                                    );
                            }
                        }
                        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
                        builder.put(MLTask.STATE_FIELD, taskState);
                        if (mlTaskCache.hasError()) {
                            builder.put(MLTask.ERROR_FIELD, mlTaskCache.getErrors().toString());
                        }
                        mlTaskManager.updateMLTask(taskId, builder.build(), 5000);

                        if (!mlTaskCache.allNodeFailed()) {
                            MLModelState modelState = mlTaskCache.hasError() ? MLModelState.PARTIALLY_LOADED : MLModelState.LOADED;
                            log.debug("load model done with state: {}, model id: {}", modelState, modelId);
                            mlModelManager
                                .updateModel(
                                    modelId,
                                    ImmutableMap
                                        .of(
                                            MLModel.MODEL_STATE_FIELD,
                                            modelState,
                                            MLModel.LAST_LOADED_TIME_FIELD,
                                            Instant.now().toEpochMilli()
                                        )
                                );
                        } else {
                            log.debug("load model failed on all nodes, model id: {}", modelId);
                        }

                        mlTaskManager.remove(taskId);
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
            log.error("Failed to execute forward action", e);
            listener.onFailure(e);
        }
    }
}
