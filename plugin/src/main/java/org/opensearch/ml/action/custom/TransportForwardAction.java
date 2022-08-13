/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom;

import java.util.List;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.action.custom.upload.MLModelUploader;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.transport.custom.MLForwardAction;
import org.opensearch.ml.common.transport.custom.MLForwardInput;
import org.opensearch.ml.common.transport.custom.MLForwardRequest;
import org.opensearch.ml.common.transport.custom.MLForwardRequestType;
import org.opensearch.ml.common.transport.custom.MLForwardResponse;
import org.opensearch.ml.common.transport.custom.upload.MLUploadInput;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.task.MLTaskDispatcher;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@Log4j2
public class TransportForwardAction extends HandledTransportAction<ActionRequest, MLForwardResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLTaskManager mlTaskManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    MLTaskDispatcher mlTaskDispatcher;
    MLIndicesHandler mlIndicesHandler;
    MLModelUploader mlModelUploader;

    @Inject
    public TransportForwardAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLTaskManager mlTaskManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLTaskDispatcher mlTaskDispatcher,
        MLIndicesHandler mlIndicesHandler,
        MLModelUploader mlModelUploader
    ) {
        super(MLForwardAction.NAME, transportService, actionFilters, MLForwardRequest::new);
        this.transportService = transportService;
        this.customModelManager = customModelManager;
        this.mlTaskManager = mlTaskManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlTaskDispatcher = mlTaskDispatcher;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlModelUploader = mlModelUploader;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLForwardResponse> listener) {
        MLForwardRequest mlForwardRequest = MLForwardRequest.fromActionRequest(request);
        MLForwardInput forwardInput = mlForwardRequest.getForwardInput();
        String modelName = forwardInput.getName();
        Integer version = forwardInput.getVersion();
        String taskId = forwardInput.getTaskId();
        String workerNodeId = forwardInput.getWorkerNodeId();
        MLTask mlTask = forwardInput.getMlTask();
        MLForwardRequestType requestType = forwardInput.getRequestType();
        String url = forwardInput.getUrl();
        try {
            switch (requestType) {
                case LOAD_MODEL_DONE:
                    List<String> workNodes = mlTaskManager.getWorkNodes(taskId);
                    if (workNodes != null) {
                        log.info("Task finished on worker node " + workerNodeId);
                        workNodes.remove(workerNodeId);
                    }

                    if (workNodes.size() == 0) {
                        log.info("All task finished on worker node");
                        mlTaskManager.updateMLTask(taskId, ImmutableMap.of(MLTask.STATE_FIELD, MLTaskState.COMPLETED), 5000);
                        mlTaskManager.remove(taskId);
                    }
                    listener.onResponse(new MLForwardResponse("ok"));
                    break;
                case UPLOAD_MODEL:
                    mlModelUploader.uploadModel(MLUploadInput.builder().name(modelName).version(version).url(url).build(), mlTask);
                    break;
                case PREDICT_MODEL:
                    String result = customModelManager.predict(forwardInput.getPredictModelInput());
                    listener.onResponse(new MLForwardResponse(result));
                    break;
                default:
                    break;
            }

        } catch (Exception e) {
            log.error("Failed to execute forward action for " + modelName, e);
            listener.onFailure(e);
        }
    }

}
