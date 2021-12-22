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
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.commons.authuser.User;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.prediction.MLPredictionTaskExecutionAction;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.parameter.Input;
import org.opensearch.ml.common.parameter.MLInput;
import org.opensearch.ml.common.parameter.MLOutput;
import org.opensearch.ml.common.parameter.MLPredictionOutput;
import org.opensearch.ml.common.parameter.Output;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.indices.MLInputDatasetHandler;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

/**
 * MLPredictTaskRunner is responsible for running predict tasks.
 */
@Log4j2
public class MLPredictTaskRunner extends MLTaskRunner<MLPredictionTaskRequest, MLPredictionTaskResponse> {
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
        MLTaskDispatcher mlTaskDispatcher
    ) {
        super(mlTaskManager, mlStats, mlTaskDispatcher);
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
    }

    @Override
    public void run(MLPredictionTaskRequest request, TransportService transportService, ActionListener<MLPredictionTaskResponse> listener) {
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
                        MLPredictionTaskExecutionAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, MLPredictionTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    /**
     * Execute algorithm and return result.
     * TODO: 1. support backend task run; 2. support dispatch task to remote node
     * @param request MLExecuteTaskRequest
     * @param transportService transport service
     * @param listener Action listener
     */
    public void execute(MLExecuteTaskRequest request, TransportService transportService, ActionListener<MLExecuteTaskResponse> listener) {
        Input input = request.getInput();
        Output output = MLEngine.execute(input);
        listener.onResponse(MLExecuteTaskResponse.builder().output(output).build());
    }

    /**
     * Start prediction task
     * @param request MLPredictionTaskRequest
     * @param listener Action listener
     */
    public void startPredictionTask(MLPredictionTaskRequest request, ActionListener<MLPredictionTaskResponse> listener) {
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .taskType(MLTaskType.PREDICTION)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
        MLInput mlInput = request.getMlInput();
        if (mlInput.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(dataFrame -> { predict(mlTask, dataFrame, request, listener); }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    mlTaskManager.addIfAbsent(mlTask);
                    mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
                    mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
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
        ActionListener<MLPredictionTaskResponse> listener
    ) {
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);

        MLInput mlInput = request.getMlInput();
        // search model by model id.
        Model model = new Model();
        if (request.getModelId() != null) {
            // Build search request to find the model by "taskId"
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            QueryBuilder queryBuilder = QueryBuilders.termQuery(TASK_ID, request.getModelId());
            searchSourceBuilder.query(queryBuilder);
            SearchRequest searchRequest = new SearchRequest(new String[] { ML_MODEL_INDEX }, searchSourceBuilder);

            // Search model.
            client.search(searchRequest, ActionListener.wrap(searchResponse -> {
                // No model found.
                if (searchResponse.getHits().getTotalHits().value == 0
                    || searchResponse.getHits().getAt(0).getSourceAsMap() == null
                    || searchResponse.getHits().getAt(0).getSourceAsMap().isEmpty()) {
                    Exception e = new ResourceNotFoundException("No model found, please check the modelId.");
                    log.error(e);
                    handlePredictFailure(mlTask, listener, e);
                    return;
                }

                Map<String, Object> source = searchResponse.getHits().getAt(0).getSourceAsMap();

                User requestUser = getUserContext(client);
                User resourceUser = User.parse((String) source.get(USER));
                if (!checkUserPermissions(requestUser, resourceUser, request.getModelId())) {
                    // The backend roles of request user and resource user doesn't have intersection
                    OpenSearchException e = new OpenSearchException(
                        "User: " + requestUser.getName() + " does not have permissions to run predict by model: " + request.getModelId()
                    );
                    log.debug(e);
                    handlePredictFailure(mlTask, listener, e);
                    return;
                }

                model.setName((String) source.get(MODEL_NAME));
                model.setVersion((Integer) source.get(MODEL_VERSION));
                byte[] decoded = Base64.getDecoder().decode((String) source.get(MODEL_CONTENT));
                model.setContent(decoded);

                // run predict
                MLOutput output;
                try {
                    mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
                    output = MLEngine.predict(mlInput.toBuilder().inputDataset(new DataFrameInputDataset(inputDataFrame)).build(), model);
                    if (output instanceof MLPredictionOutput) {
                        ((MLPredictionOutput) output).setTaskId(mlTask.getTaskId());
                        ((MLPredictionOutput) output).setStatus(mlTaskManager.get(mlTask.getTaskId()).getState().name());
                    }

                    // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                    handleMLTaskComplete(mlTask);
                } catch (Exception e) {
                    // todo need to specify what exception
                    log.error("Failed to predict " + mlInput.getAlgorithm() + ", modelId: " + model.getName(), e);
                    handlePredictFailure(mlTask, listener, e);
                    return;
                }

                MLPredictionTaskResponse response = MLPredictionTaskResponse.builder().output(output).build();
                listener.onResponse(response);
            }, searchException -> {
                log.error("Search model failed", searchException);
                handlePredictFailure(mlTask, listener, searchException);
            }));
        } else {
            IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
            log.error("ModelId is invalid", e);
            handlePredictFailure(mlTask, listener, e);
            return;
        }
    }

    private void handlePredictFailure(MLTask mlTask, ActionListener<MLPredictionTaskResponse> listener, Exception e) {
        handleMLTaskFailure(mlTask, e);
        listener.onFailure(e);
    }
}
