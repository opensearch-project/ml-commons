/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TRAIN_THREAD_POOL;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.input.MLInput;
import org.opensearch.ml.common.output.MLTrainingOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskAction;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.engine.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportResponseHandler;

import lombok.extern.log4j.Log4j2;

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
    protected final DiscoveryNodeHelper nodeHelper;
    private final MLEngine mlEngine;

    public MLTrainingTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLIndicesHandler mlIndicesHandler,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService,
        DiscoveryNodeHelper nodeHelper,
        MLEngine mlEngine
    ) {
        super(mlTaskManager, mlStats, nodeHelper, mlTaskDispatcher, mlCircuitBreakerService, clusterService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.nodeHelper = nodeHelper;
        this.mlEngine = mlEngine;
    }

    @Override
    protected String getTransportActionName() {
        return MLTrainingTaskAction.NAME;
    }

    @Override
    protected TransportResponseHandler<MLTaskResponse> getResponseHandler(ActionListener<MLTaskResponse> listener) {
        return new ActionListenerResponseHandler<>(listener, MLTaskResponse::new);
    }

    @Override
    protected void executeTask(MLTrainingTaskRequest request, ActionListener<MLTaskResponse> listener) {
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        MLTask mlTask = MLTask
            .builder()
            .taskType(MLTaskType.TRAINING)
            .inputType(inputDataType)
            .functionName(request.getMlInput().getFunctionName())
            .state(MLTaskState.CREATED)
            .workerNodes(List.of(clusterService.localNode().getId()))
            .createTime(now)
            .lastUpdateTime(now)
            .async(request.isAsync())
            .build();

        if (request.isAsync()) {
            mlTaskManager.createMLTask(mlTask, ActionListener.wrap(r -> {
                String taskId = r.getId();
                mlTask.setTaskId(taskId);
                listener.onResponse(new MLTaskResponse(new MLTrainingOutput(null, taskId, mlTask.getState().name())));
                ActionListener<MLTaskResponse> internalListener = ActionListener.wrap(res -> {
                    String modelId = ((MLTrainingOutput) res.getOutput()).getModelId();
                    mlStats
                        .createModelCounterStatIfAbsent(modelId, ActionName.TRAIN, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
                        .increment();
                    log.info("ML model trained successfully, task id: {}, model id: {}", taskId, modelId);
                    mlTask.setModelId(modelId);
                    handleAsyncMLTaskComplete(mlTask);
                }, ex -> {
                    log.error("Failed to train ML model for task " + taskId);
                    handleAsyncMLTaskFailure(mlTask, ex);
                });
                startTrainingTask(mlTask, request.getMlInput(), internalListener);
            }, e -> {
                log.error("Failed to create ML task", e);
                listener.onFailure(e);
            }));
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
    private void startTrainingTask(MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> listener) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_REQUEST_COUNT).increment();
        mlStats
            .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.TRAIN, MLActionLevelStat.ML_ACTION_REQUEST_COUNT)
            .increment();
        mlTask.setState(MLTaskState.RUNNING);
        mlTaskManager.add(mlTask);
        try {
            if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
                ActionListener<MLInputDataset> dataFrameActionListener = ActionListener.wrap(dataSet -> {
                    train(mlTask, mlInput.toBuilder().inputDataset(dataSet).build(), internalListener);
                }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    internalListener.onFailure(e);
                });
                mlInputDatasetHandler
                    .parseSearchQueryInput(
                        mlInput.getInputDataset(),
                        new ThreadedActionListener<>(log, threadPool, TRAIN_THREAD_POOL, dataFrameActionListener, false)
                    );
            } else {
                threadPool.executor(TRAIN_THREAD_POOL).execute(() -> { train(mlTask, mlInput, internalListener); });
            }
        } catch (Exception e) {
            log.error("Failed to train " + mlInput.getAlgorithm(), e);
            internalListener.onFailure(e);
        }
    }

    private void train(MLTask mlTask, MLInput mlInput, ActionListener<MLTaskResponse> actionListener) {
        ActionListener<MLTaskResponse> listener = ActionListener.wrap(r -> actionListener.onResponse(r), e -> {
            mlStats
                .createCounterStatIfAbsent(mlTask.getFunctionName(), ActionName.TRAIN, MLActionLevelStat.ML_ACTION_FAILURE_COUNT)
                .increment();
            mlStats.getStat(MLNodeLevelStat.ML_FAILURE_COUNT).increment();
            actionListener.onFailure(e);
        });
        try {
            // run training
            mlTaskManager.updateTaskStateAsRunning(mlTask.getTaskId(), mlTask.isAsync());
            MLModel mlModel = mlEngine.train(mlInput);
            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(indexCreated -> {
                if (!indexCreated) {
                    listener.onFailure(new RuntimeException("No response to create ML task index"));
                    return;
                }
                // TODO: put the user into model for backend role based access control.
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<IndexResponse> indexResponseListener = ActionListener.wrap(r -> {
                        log.info("Model saved into index, result:{}, model id: {}", r.getResult(), r.getId());
                        String returnedTaskId = mlTask.isAsync() ? mlTask.getTaskId() : null;
                        MLTrainingOutput output = new MLTrainingOutput(r.getId(), returnedTaskId, MLTaskState.COMPLETED.name());
                        listener.onResponse(MLTaskResponse.builder().output(output).build());
                    }, e -> { listener.onFailure(e); });

                    IndexRequest indexRequest = new IndexRequest(ML_MODEL_INDEX);
                    indexRequest.source(mlModel.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                    client.index(indexRequest, ActionListener.runBefore(indexResponseListener, () -> context.restore()));
                } catch (Exception e) {
                    log.error("Failed to save ML model", e);
                    listener.onFailure(e);
                }
            }, e -> {
                log.error("Failed to init ML model index", e);
                listener.onFailure(e);
            }));
        } catch (Exception e) {
            // todo need to specify what exception
            log.error("Failed to train " + mlInput.getAlgorithm(), e);
            listener.onFailure(e);
        }
    }
}
