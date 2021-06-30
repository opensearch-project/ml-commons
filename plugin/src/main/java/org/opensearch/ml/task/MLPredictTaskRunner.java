package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.OS_ML_MODEL_RESULT;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.action.prediction.MLPredictionTaskExecutionAction;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataType;
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
public class MLPredictTaskRunner extends MLTaskRunner {
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

    /**
     * Run prediction
     * @param request MLPredictionTaskRequest
     * @param transportService transport service
     * @param listener Action listener
     */
    public void runPrediction(
        MLPredictionTaskRequest request,
        TransportService transportService,
        ActionListener<MLPredictionTaskResponse> listener
    ) {
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
        if (request.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
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
                    request.getInputDataset(),
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            DataFrame inputDataFrame = mlInputDatasetHandler.parseDataFrameInput(request.getInputDataset());
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

        // search model by model id.
        Model model = new Model();
        if (request.getModelId() != null) {
            // Build search request to find the model by "taskId"
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            QueryBuilder queryBuilder = QueryBuilders.termQuery(TASK_ID, request.getModelId());
            searchSourceBuilder.query(queryBuilder);
            SearchRequest searchRequest = new SearchRequest(new String[] { OS_ML_MODEL_RESULT }, searchSourceBuilder);

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
                model.setName((String) source.get(MODEL_NAME));
                model.setVersion((Integer) source.get(MODEL_VERSION));
                byte[] decoded = Base64.getDecoder().decode((String) source.get(MODEL_CONTENT));
                model.setContent(decoded);

                // run predict
                DataFrame forecastsResults = null;
                try {
                    mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
                    forecastsResults = MLEngine.predict(request.getAlgorithm(), request.getParameters(), inputDataFrame, model);

                    // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                    handleMLTaskComplete(mlTask);
                } catch (Exception e) {
                    // todo need to specify what exception
                    log.error(e);
                    handleMLTaskFailure(mlTask, e);
                    listener.onFailure(e);
                }

                MLPredictionTaskResponse response = MLPredictionTaskResponse
                    .builder()
                    .taskId(mlTask.getTaskId())
                    .status(mlTaskManager.get(mlTask.getTaskId()).getState().name())
                    .predictionResult(forecastsResults)
                    .build();
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
