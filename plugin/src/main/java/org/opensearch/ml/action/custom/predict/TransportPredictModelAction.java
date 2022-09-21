/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.predict;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.custom_model.MLBatchModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.forward.MLForwardAction;
import org.opensearch.ml.common.transport.model.forward.MLForwardInput;
import org.opensearch.ml.common.transport.model.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.model.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.model.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.model.predict.MLPredictModelAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

@Log4j2
public class TransportPredictModelAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLTaskDispatcher mlTaskDispatcher;
    ClusterService clusterService;
    ThreadPool threadPool;

    @Inject
    public TransportPredictModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLTaskDispatcher mlTaskDispatcher,
        ClusterService clusterService,
        ThreadPool threadPool
    ) {
        super(MLPredictModelAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.transportService = transportService;
        this.customModelManager = customModelManager;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictModelRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictModelRequest.getModelId();
        MLInput input = mlPredictModelRequest.getMlInput();
        try {
            mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
//            mlTaskDispatcher.dispatchTaskWithRoundRobin(ActionListener.wrap(node -> {
                if (clusterService.localNode().getId().equals(node.getId())) {
                    // Execute ML task locally
                    log.info("Predict custom model {} locally on node {}", modelId, node.getId());
                    MLBatchModelTensorOutput result = customModelManager.predict(modelId, input);
                    listener.onResponse(MLTaskResponse.builder().output(result).build());
                } else {
                    log.info("Predict custom model {} remotely on node {}", modelId, node.getId());
                    MLForwardRequest forwardRequest = new MLForwardRequest(
                        new MLForwardInput(
                            null,
                            null,
                            null,
                            modelId,
                            node.getId(),
                            MLForwardRequestType.PREDICT_MODEL,
                            null,
                            null,
                            input
                        )
                    );
                    ActionListener<MLForwardResponse> myListener = ActionListener.wrap(res -> {
                        listener.onResponse(MLTaskResponse.builder().output(res.getMlOutput()).build());
                    }, ex -> {
                        log.error("Failed to receive response form upload model node", ex);
                        listener.onFailure(ex);
                    });

                    ThreadedActionListener threadedActionListener = new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, myListener, false);
                    transportService
                        .sendRequest(
                            node,
                            MLForwardAction.NAME,
                            forwardRequest,
                            new ActionListenerResponseHandler<MLForwardResponse>(threadedActionListener, MLForwardResponse::new)
                        );
                }
            }, e -> {
                log.error("Failed to dispatch predict custom model request " + modelId, e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Failed to download custom model " + modelId, e);
            listener.onFailure(e);
        }
    }
}
