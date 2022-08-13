/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.upload;

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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.transport.custom.MLForwardAction;
import org.opensearch.ml.common.transport.custom.MLForwardInput;
import org.opensearch.ml.common.transport.custom.MLForwardRequest;
import org.opensearch.ml.common.transport.custom.MLForwardRequestType;
import org.opensearch.ml.common.transport.custom.MLForwardResponse;
import org.opensearch.ml.common.transport.custom.load.LoadModelResponse;
import org.opensearch.ml.common.transport.custom.upload.MLUploadInput;
import org.opensearch.ml.common.transport.custom.upload.MLUploadModelAction;
import org.opensearch.ml.common.transport.custom.upload.MLUploadModelRequest;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUploadModelAction extends HandledTransportAction<ActionRequest, LoadModelResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLIndicesHandler mlIndicesHandler;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelUploader mlModelUploader;

    @Inject
    public TransportUploadModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelUploader mlModelUploader
    ) {
        super(MLUploadModelAction.NAME, transportService, actionFilters, MLUploadModelRequest::new);
        this.transportService = transportService;
        this.customModelManager = customModelManager;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelUploader = mlModelUploader;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<LoadModelResponse> listener) {
        MLUploadModelRequest uploadModelRequest = MLUploadModelRequest.fromActionRequest(request);
        MLUploadInput mlUploadInput = uploadModelRequest.getMlUploadInput();

        MLTask mlTask = MLTask
            .builder()
            .async(true)
            .taskType(MLTaskType.UPLOAD_MODEL)
            .functionName(FunctionName.CUSTOM)
            .inputType(MLInputDataType.SEARCH_QUERY)
            .createTime(Instant.now())
            .lastUpdateTime(Instant.now())
            .state(MLTaskState.CREATED)// TODO: mark task as done or failed
            .workerNode(clusterService.localNode().getId())
            .build();
        mlTaskManager.createMLTask(mlTask, ActionListener.wrap(response -> {
            String taskId = response.getId();
            mlTask.setTaskId(taskId);

            mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
                if (clusterService.localNode().getId().equals(node.getId())) {
                    // Execute ML task locally
                    log.info("Upload model {} locally on node {}", mlUploadInput.getName(), node.getId());
                    mlModelUploader.uploadModel(mlUploadInput, mlTask);
                } else {
                    // Execute ML task remotely
                    log.info("Upload model {} remotely on node {}", mlUploadInput.getName(), node.getId());
                    MLForwardRequest forwardRequest = new MLForwardRequest(
                        new MLForwardInput(
                            mlUploadInput.getName(),
                            mlUploadInput.getVersion(),
                            taskId,
                            node.getId(),
                            MLForwardRequestType.UPLOAD_MODEL,
                            mlTask,
                            mlUploadInput.getUrl(),
                            null
                        )
                    );

                    ActionListener<MLForwardResponse> myListener = ActionListener
                        .wrap(
                            res -> { log.info("Response from upload model node is " + res); },
                            ex -> { log.error("Failed to receive response form upload model node", ex); }
                        );
                    transportService
                        .sendRequest(
                            node,
                            MLForwardAction.NAME,
                            forwardRequest,
                            new ActionListenerResponseHandler<>(myListener, MLForwardResponse::new)
                        );
                }
                listener.onResponse(new LoadModelResponse(taskId, MLTaskState.CREATED.name()));
            }, e -> {
                log.error("Failed to dispatch upload model task ", e);
                listener.onFailure(e);
            }));
        }, exception -> {
            log.error("Failed to create upload model task", exception);
            listener.onFailure(exception);
        }));

    }
}
