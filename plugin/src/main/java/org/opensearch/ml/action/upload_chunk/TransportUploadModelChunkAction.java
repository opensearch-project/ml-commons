/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.upload_chunk;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkInput;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.ml.common.transport.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUploadModelChunkAction extends HandledTransportAction<ActionRequest, MLUploadModelChunkResponse> {
    TransportService transportService;
    ActionFilters actionFilters;
    MLModelChunkUploader mlModelChunkUploader;

    @Inject
    public TransportUploadModelChunkAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelChunkUploader mlModelChunkUploader
    ) {
        super(MLUploadModelChunkAction.NAME, transportService, actionFilters, MLUploadModelChunkRequest::new);
        this.transportService = transportService;
        this.actionFilters = actionFilters;
        this.mlModelChunkUploader = mlModelChunkUploader;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUploadModelChunkResponse> listener) {
        MLUploadModelChunkRequest uploadModelRequest = MLUploadModelChunkRequest.fromActionRequest(request);
        MLUploadModelChunkInput mlUploadChunkInput = uploadModelRequest.getUploadModelChunkInput();

        mlModelChunkUploader.uploadModelChunk(mlUploadChunkInput, listener);
    }
}
