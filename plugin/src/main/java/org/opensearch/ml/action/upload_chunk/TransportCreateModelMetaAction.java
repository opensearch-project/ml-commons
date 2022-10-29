/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLCreateModelMetaResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportCreateModelMetaAction extends HandledTransportAction<ActionRequest, MLCreateModelMetaResponse> {

    TransportService transportService;
    ActionFilters actionFilters;
    MLModelMetaCreate mlModelMetaCreate;

    @Inject
    public TransportCreateModelMetaAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelMetaCreate mlModelMetaCreate
    ) {
        super(MLCreateModelMetaAction.NAME, transportService, actionFilters, MLCreateModelMetaRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlModelMetaCreate = mlModelMetaCreate;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLCreateModelMetaResponse> listener) {
        MLCreateModelMetaRequest createModelMetaRequest = MLCreateModelMetaRequest.fromActionRequest(request);
        MLCreateModelMetaInput mlUploadInput = createModelMetaRequest.getMlCreateModelMetaInput();
        mlModelMetaCreate
            .createModelMeta(
                mlUploadInput,
                ActionListener
                    .wrap(modelId -> { listener.onResponse(new MLCreateModelMetaResponse(modelId, MLTaskState.CREATED.name())); }, ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    })
            );
    }
}
