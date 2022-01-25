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

import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.model.MLModel;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * MLTrainingTaskRunner is responsible for running training tasks.
 */
@Log4j2
public class MLTrainingTaskRunner extends MLTaskRunner<MLTrainingTaskRequest, MLTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLInputDatasetHandler mlInputDatasetHandler;

    public MLTrainingTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLIndicesHandler mlIndicesHandler,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
    }

    @Override
    public void run(MLTrainingTaskRequest request, TransportService transportService, ActionListener<MLTaskResponse> listener) {
        mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute training task locally
                log.info("execute ML training request {} locally on node {}", request.toString(), node.getId());
                createMLTaskAndTrain(request, listener);
            } else {
                // Execute batch task remotely
                log.info("execute ML training request {} remotely on node {}", request.toString(), node.getId());
                transportService
                    .sendRequest(
                        node,
                        MLTrainingTaskAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, MLTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    public void createMLTaskAndTrain(MLTrainingTaskRequest request, ActionListener<MLTaskResponse> listener) {
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        MLTask mlTask = MLTask
            .builder()
            .taskType(MLTaskType.TRAINING)
            .inputType(inputDataType)
            .functionName(request.getMlInput().getFunctionName())
            .state(MLTaskState.CREATED)
            .workerNode(clusterService.localNode().getId())
            .createTime(now)
            .lastUpdateTime(now)
            .async(request.isAsync())
            .build();

        if (request.isAsync()) {
            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(r -> {
                String taskId = r.getId();
                mlTask.setTaskId(taskId);
                if (mlTask.isAsync()) {
                    listener.onResponse(new MLTaskResponse(new MLTrainingOutput(null, taskId, mlTask.getState().name())));
                    ActionListener<MLTaskResponse> internalListener = ActionListener.wrap(res -> {
                        String modelId = ((MLTrainingOutput) res.getOutput()).getModelId();
                        log.info("ML model trained successfully, task id: {}, model id: {}", taskId, modelId);
                        mlTask.setModelId(modelId);
                        handleMLTaskComplete(mlTask);
                    }, ex -> {
                        log.error("Failed to train ML model for task " + taskId);
                        handleMLTaskFailure(mlTask, ex);
                    });
                    startTrainingTask(mlTask, request.getMlInput(), internalListener);
                } else {
                    startTrainingTask(mlTask, request.getMlInput(), listener);
                }
            }, e -> { listener.onFailure(e); }));
        } else {
            mlTask.setTaskId(UUID.randomUUID().toString());
            startTrainingTask(mlTask, request.getMlInput(), listener);
        }
    }

    /**
     * Start training task
     * @param mlTask ML task
     * @param mlInput ML input
     * @param listener Action listener
     */
    public void startTrainingTask(MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> listener) {
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);
        if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(
                    dataFrame -> {
                        train(mlTask, mlInput.toBuilder().inputDataset(new DataFrameInputDataset(dataFrame)).build(), listener);
                    },
                    e -> {
                        log.error("Failed to generate DataFrame from search query", e);
                        mlTaskManager.addIfAbsent(mlTask);
                        handleMLTaskFailure(mlTask, e);
                        listener.onFailure(e);
                    }
                );
            mlInputDatasetHandler
                .parseSearchQueryInput(
                    mlInput.getInputDataset(),
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            threadPool.executor(TASK_THREAD_POOL).execute(() -> { train(mlTask, mlInput, listener); });
        }
    }

    private void train(MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> listener) {
        try {
            // run training
            mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING, mlTask.isAsync());
            Model model = MLEngine.train(mlInput);
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(indexCreated -> {
                if (!indexCreated) {
                    listener.onFailure(new RuntimeException("No response to create ML task index"));
                    return;
                }
                // TODO: put the user into model for backend role based access control.
                MLModel mlModel = new MLModel(mlInput.getAlgorithm(), model);
                IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX)
                    .source(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS))
                    .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(indexRequest, ActionListener.wrap(r -> {
                    log.info("mode data indexing done, result:{}, model id: {}", r.getResult(), r.getId());
                    handleMLTaskComplete(mlTask);
                    MLTrainingOutput output = new MLTrainingOutput(r.getId(), mlTask.getTaskId(), MLTaskState.COMPLETED.name());
                    listener.onResponse(MLTaskResponse.builder().output(output).build());
                }, e -> {
                    handleMLTaskFailure(mlTask, e);
                    listener.onFailure(e);
                }));
            }, e -> { listener.onFailure(e); }));
        } catch (Exception e) {
            // todo need to specify what exception
            log.error("Failed to train " + mlInput.getAlgorithm(), e);
            handleMLTaskFailure(mlTask, e);
            listener.onFailure(e);
        }
    }
}
