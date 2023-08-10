/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.training;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.task.MLTrainingTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportTrainingTaskAction extends HandledTransportAction<ActionRequest, MLTaskResponse> {
    MLTaskRunner<MLTrainingTaskRequest, MLTaskResponse> mlTrainingTaskRunner;
    TransportService transportService;

    @Inject
    public TransportTrainingTaskAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLTrainingTaskRunner mlTrainingTaskRunner
    ) {
        super(MLTrainingTaskAction.NAME, transportService, actionFilters, MLTrainingTaskRequest::new);
        this.mlTrainingTaskRunner = mlTrainingTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLTaskResponse> listener) {
        MLTrainingTaskRequest trainingRequest = MLTrainingTaskRequest.fromActionRequest(request);
        mlTrainingTaskRunner.run(trainingRequest.getMlInput().getFunctionName(), trainingRequest, transportService, listener);
    }
}
