/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

import java.time.Instant;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLOutput;
import org.opensearch.ml.common.output.MLPredictionOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.trainpredict.MLTrainAndPredictionTaskAction;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportResponseHandler;

/**
 * MLPredictTaskRunner is responsible for running predict tasks.
 */
@Log4j2
public class MLTrainAndPredictTaskRunner extends MLTaskRunner<MLTrainingTaskRequest, MLTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;

    public MLTrainAndPredictTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
    }

    @Override
    protected String getTransportActionName() {
        return MLTrainAndPredictionTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseHandler(ActionListener<MLTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLTaskResponse::new);
    }

    /**
     * Start prediction task
     * @param request MLPredictionTaskRequest
     * @param listener Action listener
     */
    @Override
    protected void executeTask(MLTrainingTaskRequest request, ActionListener<MLTaskResponse> listener) {
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .taskType(MLTaskType.TRAINING_AND_PREDICTION)
            .inputType(inputDataType)
            .functionName(request.getMlInput().getFunctionName())
            .state(MLTaskState.CREATED)
            .workerNode(clusterService.localNode().getId())
            .createTime(now)
            .lastUpdateTime(now)
            .async(false)
            .build();
        MLInput mlInput = request.getMlInput();
        MLInputDataset inputDataset = mlInput.getInputDataset();
        if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<MLInputDataset> dataFrameActionListener = ActionListener.wrap(dataSet -> {
                MLInput newInput = mlInput.toBuilder().inputDataset(dataSet).build();
                trainAndPredict(mlTask, newInput, listener);
            }, e -> {
                log.error("Failed to generate DataFrame from search query", e);
                handlePredictFailure(mlTask, listener, e, false);
            });
            mlInputDatasetHandler
                .parseSearchQueryInput(
                    inputDataset,
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            threadPool.executor(TASK_THREAD_POOL).execute(() -> { trainAndPredict(mlTask, mlInput, listener); });
        }
    }

    private void trainAndPredict(MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> listener) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();
        mlStats
            .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.TRAIN_PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
            .increment();
        mlTaskManager.add(mlTask);

        // run train and predict
        try {
            mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING, mlTask.isAsync());
            MLOutput output = MLEngine.trainAndPredict(mlInput);
            handleAsyncMLTaskComplete(mlTask);
            if (output instanceof MLPredictionOutput) {
                ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
            }

            MLTaskResponse response = MLTaskResponse.builder().output(output).build();
            log.info("Train and predict task done for algorithm: {}, task id: {}", mlTask.getFunctionName(), mlTask.getTaskId());
            internalListener.onResponse(response);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error("Failed to train and predict " + mlInput.getAlgorithm(), e);
            handlePredictFailure(mlTask, listener, e, true);
            return;
        }
    }

    private void handlePredictFailure(MLTask mlTask, ActionListener<MLTaskResponse> listener, Exception e, boolean trackFailure) {
        if (trackFailure) {
            mlStats
                .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.TRAIN_PREDICT, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                .increment();
            mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_FAILURE_COUNT).increment();
        }
        handleAsyncMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
