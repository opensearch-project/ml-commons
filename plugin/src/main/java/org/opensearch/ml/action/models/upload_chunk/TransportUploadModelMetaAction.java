/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models.upload_chunk;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaAction;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaInput;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaRequest;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelMetaResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUploadModelMetaAction extends HandledTransportAction<ActionRequest, MLUploadModelMetaResponse> {

    TransportService transportService;
    ModelHelper modelHelper;
    MLIndicesHandler mlIndicesHandler;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    Client client;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelMetaUploader mlModelMetaUploader;

    @Inject
    public TransportUploadModelMetaAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        Client client,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelMetaUploader mlModelMetaUploader
    ) {
        super(MLUploadModelMetaAction.NAME, transportService, actionFilters, MLUploadModelMetaRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.client = client;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelMetaUploader = mlModelMetaUploader;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUploadModelMetaResponse> listener) {
        MLUploadModelMetaRequest uploadModelMetaRequest = MLUploadModelMetaRequest.fromActionRequest(request);
        MLUploadModelMetaInput mlUploadInput = uploadModelMetaRequest.getMlUploadModelMetaInput();
        mlModelMetaUploader
            .uploadModelMeta(
                mlUploadInput,
                ActionListener
                    .wrap(modelId -> { listener.onResponse(new MLUploadModelMetaResponse(modelId, MLTaskState.CREATED.name())); }, ex -> {
                        log.error("Failed to init model index", ex);
                        listener.onFailure(ex);
                    })
            );
    }
}
