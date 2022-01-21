/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.trainpredict;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.task.MLTrainAndPredictTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class MLTrainAndPredictionTaskTransportAction extends HandledTransportAction<ActionRequest, MLPredictionTaskResponse> {
    private final MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner;
    private final TransportService transportService;

    @Inject
    public MLTrainAndPredictionTaskTransportAction(
        ActionFilters actionFilters,
        TransportService transportService,
        MLTrainAndPredictTaskRunner mlTrainAndPredictTaskRunner
    ) {
        super(MLTrainAndPredictionTaskAction.NAME, transportService, actionFilters, MLTrainingTaskRequest::new);
        this.mlTrainAndPredictTaskRunner = mlTrainAndPredictTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLPredictionTaskResponse> listener) {
        MLTrainingTaskRequest trainingRequest = MLTrainingTaskRequest.fromActionRequest(request);
        mlTrainAndPredictTaskRunner.startTrainAndPredictionTask(trainingRequest, listener);
    }
}
