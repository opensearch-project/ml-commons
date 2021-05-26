package org.opensearch.ml.action.prediction;

import org.opensearch.ml.task.MLTaskRunner;
import org.opensearch.ml.action.prediction.MLPredictionTaskRemoteExecutionAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class MLPredictionTaskRemoteExecutionTransportAction extends HandledTransportAction<MLPredictionTaskRequest, MLPredictionTaskResponse> {
    private final MLTaskRunner mlTaskRunner;
    private final TransportService transportService;

    @Inject
    public MLPredictionTaskRemoteExecutionTransportAction(
            ActionFilters actionFilters,
            TransportService transportService,
            MLTaskRunner mlTaskRunner
    ) {
        super(MLPredictionTaskRemoteExecutionAction.NAME, transportService, actionFilters, MLPredictionTaskRequest::new);
        this.mlTaskRunner = mlTaskRunner;
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, MLPredictionTaskRequest request, ActionListener<MLPredictionTaskResponse> listener) {
        mlTaskRunner.startPredictionTask(request, listener);
    }
}
