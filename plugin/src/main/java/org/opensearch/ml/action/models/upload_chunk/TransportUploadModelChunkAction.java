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
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadChunkInput;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelChunkAction;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelChunkRequest;
import org.opensearch.ml.common.transport.model.upload_chunk.MLUploadModelChunkResponse;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUploadModelChunkAction extends HandledTransportAction<ActionRequest, MLUploadModelChunkResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLIndicesHandler mlIndicesHandler;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    MLTaskDispatcher mlTaskDispatcher;
    MLModelChunkUploader mlModelUploader;

    @Inject
    public TransportUploadModelChunkAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLIndicesHandler mlIndicesHandler,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        MLTaskDispatcher mlTaskDispatcher,
        MLModelChunkUploader mlModelUploader
    ) {
        super(MLUploadModelChunkAction.NAME, transportService, actionFilters, MLUploadModelChunkRequest::new);
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlModelUploader = mlModelUploader;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLUploadModelChunkResponse> listener) {
        MLUploadModelChunkRequest uploadModelRequest = MLUploadModelChunkRequest.fromActionRequest(request);
        MLUploadChunkInput mlUploadChunkInput = uploadModelRequest.getMlUploadInput();

        mlModelUploader.uploadModel(mlUploadChunkInput, listener);
    }
}
