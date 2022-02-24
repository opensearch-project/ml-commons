/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.ML_MODEL_INDEX;
import static org.opensearch.ml.permission.AccessController.checkUserPermissions;
import static org.opensearch.ml.permission.AccessController.getUserContext;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.OpenSearchException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.ml.common.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLModel;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.parameter.MLTask;
import org.opensearch.ml.common.parameter.MLTaskState;
import org.opensearch.ml.common.parameter.MLTaskType;
import org.opensearch.ml.common.parameter.Model;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * MLPredictTaskRunner is responsible for running predict tasks.
 */
@Log4j2
public class MLPredictTaskRunner extends MLTaskRunner<MLPredictionTaskRequest, MLTaskResponse> {
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLInputDatasetHandler mlInputDatasetHandler;

    public MLPredictTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLInputDatasetHandler mlInputDatasetHandler,
        MLTaskDispatcher mlTaskDispatcher,
        MLCircuitBreakerService mlCircuitBreakerService
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher, mlCircuitBreakerService);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
    }

    @Override
    public void executeTask(MLPredictionTaskRequest request, TransportService transportService, ActionListener<MLTaskResponse> listener) {
        mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute prediction task locally
                log.info("execute ML prediction request {} locally on node {}", request.toString(), node.getId());
                startPredictionTask(request, listener);
            } else {
                // Execute batch task remotely
                log.info("execute ML prediction request {} remotely on node {}", request.toString(), node.getId());
                transportService
                    .sendRequest(
                        node,
                        MLPredictionTaskAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, MLTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    /**
     * Start prediction task
     * @param request MLPredictionTaskRequest
     * @param listener Action listener
     */
    public void startPredictionTask(MLPredictionTaskRequest request, ActionListener<MLTaskResponse> listener) {
        MLInputDataType inputDataType = request.getMlInput().getInputDataset().getInputDataType();
        Instant now = Instant.now();
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .modelId(request.getModelId())
            .taskType(MLTaskType.PREDICTION)
            .inputType(inputDataType)
            .functionName(request.getMlInput().getFunctionName())
            .state(MLTaskState.CREATED)
            .workerNode(clusterService.localNode().getId())
            .createTime(now)
            .lastUpdateTime(now)
            .async(false)
            .build();
        MLInput mlInput = request.getMlInput();
        if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(dataFrame -> { predict(mlTask, dataFrame, request, listener); }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    handleMLTaskFailure(mlTask, e);
                    listener.onFailure(e);
                });
            mlInputDatasetHandler
                .parseSearchQueryInput(
                    mlInput.getInputDataset(),
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            DataFrame inputDataFrame = mlInputDatasetHandler.parseDataFrameInput(mlInput.getInputDataset());
            threadPool.executor(TASK_THREAD_POOL).execute(() -> { predict(mlTask, inputDataFrame, request, listener); });
        }
    }

    private void predict(
        MLTask mlTask,
        DataFrame inputDataFrame,
        MLPredictionTaskRequest request,
        ActionListener<MLTaskResponse> listener
    ) {
        ActionListener<MLTaskResponse> internalListener = wrappedCleanupListener(listener, mlTask.getTaskId());
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);
        MLInput mlInput = request.getMlInput();

        // run predict
        try {
            // search model by model id.
            Model model = new Model();
            if (request.getModelId() != null) {
                GetRequest getRequest = new GetRequest(ML_MODEL_INDEX, mlTask.getModelId());
                try (ThreadContext.StoredContext context = threadPool.getThreadContext().stashContext()) {
                    client.get(getRequest, ActionListener.wrap(r -> {
                        if (r == null || !r.isExists()) {
                            internalListener.onFailure(new ResourceNotFoundException("No model found, please check the modelId."));
                            return;
                        }
                        Map<String, Object> source = r.getSourceAsMap();
                        User requestUser = getUserContext(client);
                        User resourceUser = User.parse((String) source.get(USER));
                        if (!checkUserPermissions(requestUser, resourceUser, request.getModelId())) {
                            // The backend roles of request user and resource user doesn't have intersection
                            OpenSearchException e = new OpenSearchException(
                                "User: "
                                    + requestUser.getName()
                                    + " does not have permissions to run predict by model: "
                                    + request.getModelId()
                            );
                            log.debug(e);
                            handlePredictFailure(mlTask, internalListener, e);
                            return;
                        }

                        model.setName((String) source.get(MLModel.MODEL_NAME));
                        model.setVersion((Integer) source.get(MLModel.MODEL_VERSION));
                        byte[] decoded = Base64.getDecoder().decode((String) source.get(MLModel.MODEL_CONTENT));
                        model.setContent(decoded);

                        // run predict
                        MLOutput output;
                        try {
                            mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING, mlTask.isAsync());
                            output = MLEngine
                                .predict(mlInput.toBuilder().inputDataset(new DataFrameInputDataset(inputDataFrame)).build(), model);
                            if (output instanceof MLPredictionOutput) {
                                ((MLPredictionOutput) output).setTaskId(mlTask.getTaskId());
                                ((MLPredictionOutput) output).setStatus(MLTaskState.COMPLETED.name());
                            }

                            // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                            handleMLTaskComplete(mlTask);
                        } catch (Exception e) {
                            // todo need to specify what exception
                            log.error("Failed to predict " + mlInput.getAlgorithm() + ", modelId: " + model.getName(), e);
                            handlePredictFailure(mlTask, internalListener, e);
                            return;
                        }

                        MLTaskResponse response = MLTaskResponse.builder().output(output).build();
                        internalListener.onResponse(response);
                    }, e -> {
                        log.error("Failed to predict model " + mlTask.getModelId(), e);
                        internalListener.onFailure(e);
                    }));
                } catch (Exception e) {
                    log.error("Failed to get model " + mlTask.getModelId(), e);
                    internalListener.onFailure(e);
                }
            } else {
                IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
                log.error("ModelId is invalid", e);
                handlePredictFailure(mlTask, internalListener, e);
                return;
            }
        } catch (Exception e) {
            log.error("Failed to predict " + mlInput.getAlgorithm(), e);
            internalListener.onFailure(e);
        }
    }

    private void handlePredictFailure(MLTask mlTask, ActionListener<MLTaskResponse> listener, Exception e) {
        handleMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
