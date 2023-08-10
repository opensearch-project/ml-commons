/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.trainpredict;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.task.MLTrainAndPredictTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportTrainAndPredictionTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    private final MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner;
    private final TransportService transportService;

    @Inject
    public TransportTrainAndPredictionTaskAction(
        ActionFilters actionFilters,
        TransportService transportService,
        MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner
    ) {
        super(MLTrainAndPredictionTaskAction.NAME, transportService, actionFilters, MLTrainingTaskRequest::new);
        this.mlTrainAndPredictTaskRunner = mlTrainAndPredictTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLTrainingTaskRequest trainingRequest = MLTrainingTaskRequest.fromActionRequest(request);
        mlTrainAndPredictTaskRunner.run(trainingRequest.getMlInput().getFunctionName(), trainingRequest, transportService, listener);
    }
}
