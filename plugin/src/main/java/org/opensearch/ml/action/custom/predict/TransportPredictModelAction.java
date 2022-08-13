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
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.custom.MLForwardAction;
import org.opensearch.ml.common.transport.custom.MLForwardInput;
import org.opensearch.ml.common.transport.custom.MLForwardRequest;
import org.opensearch.ml.common.transport.custom.MLForwardRequestType;
import org.opensearch.ml.common.transport.custom.MLForwardResponse;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelAction;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelInput;
import org.opensearch.ml.common.transport.custom.predict.MLPredictModelRequest;
import org.opensearch.ml.common.transport.custom.predict.PredictModelResponse;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportPredictModelAction extends HandledTransportAction<ActionRequest, PredictModelResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLTaskDispatcher mlTaskDispatcher;
    ClusterService clusterService;

    @Inject
    public TransportPredictModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLTaskDispatcher mlTaskDispatcher,
        ClusterService clusterService
    ) {
        super(MLPredictModelAction.NAME, transportService, actionFilters, MLPredictModelRequest::new);
        this.transportService = transportService;
        this.customModelManager = customModelManager;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<PredictModelResponse> listener) {
        MLPredictModelRequest mlPredictModelRequest = MLPredictModelRequest.fromActionRequest(request);
        MLPredictModelInput input = mlPredictModelRequest.getMlPredictModelInput();
        try {
            mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
                if (clusterService.localNode().getId().equals(node.getId())) {
                    // Execute ML task locally
                    log.info("Predict custom model {} locally on node {}", input.getModelName(), node.getId());
                    String result = customModelManager.predict(input);
                    listener.onResponse(new PredictModelResponse(result));
                } else {
                    MLForwardRequest forwardRequest = new MLForwardRequest(
                        new MLForwardInput(
                            input.getModelName(),
                            input.getVersion(),
                            null,
                            node.getId(),
                            MLForwardRequestType.PREDICT_MODEL,
                            null,
                            null,
                            input
                        )
                    );
                    ActionListener<MLForwardResponse> myListener = ActionListener.wrap(res -> {
                        log.info("Response from predict model node is " + res);
                        listener.onResponse(new PredictModelResponse(res.getResult()));
                    }, ex -> {
                        log.error("Failed to receive response form upload model node", ex);
                        listener.onFailure(ex);
                    });
                    transportService
                        .sendRequest(
                            node,
                            MLForwardAction.NAME,
                            forwardRequest,
                            new ActionListenerResponseHandler<MLForwardResponse>(myListener, MLForwardResponse::new)
                        );
                }
            }, e -> {
                log.error("Failed to dispatch predict custom model request " + input.getModelName(), e);
                listener.onFailure(e);
            }));

        } catch (Exception e) {
            log.error("Failed to download custom model " + input.getModelName(), e);
            listener.onFailure(e);
        }
    }
}
