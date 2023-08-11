/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportPredictionTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mlPredictTaskRunner;
    TransportService transportService;
    MLModelCacheHelper modelCacheHelper;

    @Inject
    public TransportPredictionTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLPredictTaskRunner mlPredictTaskRunner,
        MLModelCacheHelper modelCacheHelper
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.modelCacheHelper = modelCacheHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        String modelId = mlPredictionTaskRequest.getModelId();
        String requestId = mlPredictionTaskRequest.getRequestID();
        log.debug("receive predict request " + requestId + " for model " + mlPredictionTaskRequest.getModelId());
        long startTime = System.nanoTime();
        mlPredictTaskRunner.run(mlPredictionTaskRequest, transportService, ActionListener.runAfter(listener, () -> {
            long endTime = System.nanoTime();
            double durationInMs = (endTime - startTime) / 1e6;
            modelCacheHelper.addPredictRequestDuration(modelId, durationInMs);
            log.debug("completed predict request " + requestId + " for model " + modelId);
        }));
    }
}
