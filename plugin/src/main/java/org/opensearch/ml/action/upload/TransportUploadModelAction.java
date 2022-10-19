/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload;

import java.time.Instant;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.upload.MLUploadInput;
import org.opensearch.ml.common.transport.upload.MLUploadModelAction;
import org.opensearch.ml.common.transport.upload.MLUploadModelRequest;
import org.opensearch.ml.common.transport.upload.UploadModelResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUploadModelAction extends HandledTransportAction<ActionRequest, UploadModelResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLIndicesHandler mlIndicesHandler;
    MLModelManager mlModelManager;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    DiscoveryNodeHelper nodeFilter;
    MLTaskDispatcher mlTaskDispatcher;
    MLStats mlStats;

    @Inject
    public TransportUploadModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLIndicesHandler mlIndicesHandler,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        DiscoveryNodeHelper nodeFilter,
        MLTaskDispatcher mlTaskDispatcher,
        MLStats mlStats
    ) {
        super(MLUploadModelAction.NAME, transportService, actionFilters, MLUploadModelRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.nodeFilter = nodeFilter;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlStats = mlStats;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UploadModelResponse> listener) {
        MLUploadModelRequest uploadModelRequest = MLUploadModelRequest.fromActionRequest(request);
        MLUploadInput mlUploadInput = uploadModelRequest.getMlUploadInput();
        // mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();
        // //TODO: track executing task; track upload failures
        // mlStats.createCounterStatIfAbsent(FunctionName.TEXT_EMBEDDING, ActionName.UPLOAD,
        // MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        MLTask mlTask = MLTask
            .builder()
            .async(true)
            .taskType(MLTaskType.UPLOAD_MODEL)
            .functionName(mlUploadInput.getFunctionName())
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .state(MLTaskState.CREATED)
            .workerNode(clusterService.localNode().getId())
            .build();

        mlTaskDispatcher.dispatch(ActionListener.wrap(node -> {
            String nodeId = node.getId();
            mlTask.setWorkerNode(nodeId);

            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
                String taskId = response.getId();
                mlTask.setTaskId(taskId);
                listener.onResponse(new UploadModelResponse(taskId, MLTaskState.CREATED.name()));

                if (clusterService.localNode().getId().equals(nodeId)) {
                    mlModelManager.uploadMLModel(mlUploadInput, mlTask);
                } else {
                    MLForwardInput forwardInput = MLForwardInput
                        .builder()
                        .requestType(MLForwardRequestType.UPLOAD_MODEL)
                        .uploadInput(mlUploadInput)
                        .mlTask(mlTask)
                        .build();
                    MLForwardRequest forwardRequest = new MLForwardRequest(forwardInput);
                    ActionListener<MLForwardResponse> myListener = ActionListener
                        .wrap(
                            res -> { log.debug("Response from model node: " + res); },
                            ex -> { log.error("Failure from model node", ex); }
                        );
                    transportService
                        .sendRequest(
                            node,
                            MLForwardAction.NAME,
                            forwardRequest,
                            new ActionListenerResponseHandler<>(myListener, MLForwardResponse::new)
                        );
                }
            }, exception -> {
                log.error("Failed to create upload model task", exception);
                listener.onFailure(exception);
            }));
        }, e -> {
            log.error("Failed to dispatch upload model task ", e);
            listener.onFailure(e);
        }));

    }
}
