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
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaAction;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaInput;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLRegisterModelMetaResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportRegisterModelMetaAction extends HandledTransportAction<ActionRequest, MLRegisterModelMetaResponse> {

    TransportService transportService;
    ActionFilters actionFilters;
    MLModelManager mlModelManager;

    @Inject
    public TransportRegisterModelMetaAction(TransportService transportService, ActionFilters actionFilters, MLModelManager mlModelManager) {
        super(MLRegisterModelMetaAction.NAME, transportService, actionFilters, MLRegisterModelMetaRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlModelManager = mlModelManager;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLRegisterModelMetaResponse> listener) {
        MLRegisterModelMetaRequest registerModelMetaRequest = MLRegisterModelMetaRequest.fromActionRequest(request);
        MLRegisterModelMetaInput mlUploadInput = registerModelMetaRequest.getMlRegisterModelMetaInput();
        mlModelManager
            .registerModelMeta(
                mlUploadInput,
                ActionListener
                    .wrap(modelId -> { listener.onResponse(new MLRegisterModelMetaResponse(modelId, MLTaskState.CREATED.name())); }, ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    })
            );
    }
}
