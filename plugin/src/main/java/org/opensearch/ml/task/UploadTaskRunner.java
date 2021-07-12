/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.OS_ML_MODEL_RESULT;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.upload.UploadTaskExecutionAction;
import org.opensearch.ml.common.transport.upload.UploadTaskRequest;
import org.opensearch.ml.common.transport.upload.UploadTaskResponse;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * UploadTaskRunner is responsible for running upload tasks.
 */
@Log4j2
public class UploadTaskRunner extends MLTaskRunner {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLIndicesHandler mlIndicesHandler;

    public UploadTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLIndicesHandler mlIndicesHandler,
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
    }

    /**
     * Run upload
     *
     * @param request          UploadTaskRequest
     * @param transportService transport service
     * @param listener         Action listener
     */
    public void runUpload(UploadTaskRequest request, TransportService transportService, ActionListener<UploadTaskResponse> listener) {
        mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute prediction task locally
                log.info("execute upload request {} locally on node {}", request.toString(), node.getId());
                startUploadTask(request, listener);
            } else {
                // Execute batch task remotely
                log.info("execute upload request {} remotely on node {}", request.toString(), node.getId());
                transportService
                    .sendRequest(
                        node,
                        UploadTaskExecutionAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, UploadTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    /**
     * Start upload task
     *
     * @param request  UploadTaskRequest
     * @param listener Action listener
     */
    public void startUploadTask(UploadTaskRequest request, ActionListener<UploadTaskResponse> listener) {
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .taskType(MLTaskType.UPLOADING)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
        threadPool.executor(TASK_THREAD_POOL).execute(() -> { upload(mlTask, request, listener); });
    }

    private void upload(MLTask mlTask, UploadTaskRequest request, ActionListener<UploadTaskResponse> listener) {
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);

        // run upload
        try {
            mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
            mlIndicesHandler.initModelIndexIfAbsent();
            Map<String, Object> source = new HashMap<>();
            source.put(TASK_ID, mlTask.getTaskId());
            source.put(ALGORITHM, request.getAlgorithm());
            source.put(MODEL_VERSION, request.getVersion());
            source.put(MODEL_NAME, request.getName());
            source.put(MODEL_FORMAT, request.getFormat());
            source.put(MODEL_CONTENT, request.getBody());
            // TODO: make sure this doesn't block the thread, use index()
            IndexResponse indexResponse = client.prepareIndex(OS_ML_MODEL_RESULT, "_doc").setSource(source).get();
            // IndexRequest indexRequest = new IndexRequest(OS_ML_MODEL_RESULT, "_doc");
            // client.index(indexRequest, ActionListener.wrap(indexResponse -> {}));
            log.info("mode data indexing done, result:{}", indexResponse.getResult());
            handleMLTaskComplete(mlTask);
            UploadTaskResponse taskResponse = UploadTaskResponse.builder().modelId(mlTask.getTaskId()).build();
            listener.onResponse(taskResponse);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error(e);
            handleUploadFailure(mlTask, listener, e);
        }
    }

    private void handleUploadFailure(MLTask mlTask, ActionListener<UploadTaskResponse> listener, Exception e) {
        handleMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
