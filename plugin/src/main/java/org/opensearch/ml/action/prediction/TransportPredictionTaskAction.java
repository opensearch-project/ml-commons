/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.prediction;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.model.predict.MLPredictModelAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.task.MLPredictTaskRunner;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class TransportPredictionTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> mlPredictTaskRunner;
    TransportService transportService;
    Client client;

    @Inject
    public TransportPredictionTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLPredictTaskRunner mlPredictTaskRunner,
        Client client
    ) {
        super(MLPredictionTaskAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlPredictTaskRunner = mlPredictTaskRunner;
        this.transportService = transportService;
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLPredictionTaskRequest mlPredictionTaskRequest = MLPredictionTaskRequest.fromActionRequest(request);
        MLInput mlInput = mlPredictionTaskRequest.getMlInput();
        if (mlInput.getFunctionName() == FunctionName.CUSTOM) {
            client.execute(MLPredictModelAction.INSTANCE, request, listener);
        } else {
            mlPredictTaskRunner.run(mlPredictionTaskRequest, transportService, listener);
        }
    }
}
