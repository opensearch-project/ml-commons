package org.opensearch.ml.action.batch;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionAction;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionRequest;
import org.opensearch.ml.common.transport.batch.MLBatchIngestionResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportBatchIngestionAction extends HandledTransportAction<ActionRequest, MLBatchIngestionResponse> {

    TransportService transportService;
    @Inject
    public TransportBatchIngestionAction(
        TransportService transportService,
        ActionFilters actionFilters
    ) {
        super(MLBatchIngestionAction.NAME, transportService, actionFilters, MLBatchIngestionRequest::new);
        this.transportService = transportService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLBatchIngestionResponse> listener) {
        MLBatchIngestionRequest mlBatchIngestionRequest = MLBatchIngestionRequest.fromActionRequest(request);


    }
}
