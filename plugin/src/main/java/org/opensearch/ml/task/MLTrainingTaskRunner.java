package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.OS_ML_MODEL_RESULT;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.training.MLTrainingTaskExecutionAction;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.indices.MLInputDatasetHandler;
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
public class MLTrainingTaskRunner extends MLTaskRunner {
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

    /**
     * Run training
     * @param request MLTrainingTaskRequest
     * @param transportService transport service
     * @param listener Action listener
     */
    public void runTraining(
        MLTrainingTaskRequest request,
        TransportService transportService,
        ActionListener<MLTrainingTaskResponse> listener
    ) {
        mlTaskDispatcher.dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute training task locally
                log.info("execute ML training request {} locally on node {}", request.toString(), node.getId());
                startTrainingTask(request, listener);
            } else {
                // Execute batch task remotely
                log.info("execute ML training request {} remotely on node {}", request.toString(), node.getId());
                transportService
                    .sendRequest(
                        node,
                        MLTrainingTaskExecutionAction.NAME,
                        request,
                        new ActionListenerResponseHandler<>(listener, MLTrainingTaskResponse::new)
                    );
            }
        }, e -> listener.onFailure(e)));
    }

    /**
     * Start training task
     * @param request MLTrainingTaskRequest
     * @param listener Action listener
     */
    public void startTrainingTask(MLTrainingTaskRequest request, ActionListener<MLTrainingTaskResponse> listener) {
        MLTask mlTask = MLTask
            .builder()
            .taskId(UUID.randomUUID().toString())
            .taskType(MLTaskType.TRAINING)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
        // TODO: move this listener onResponse later to catch the following cases:
        // 1). search data failure, 2) train model failure, 3) persist model failure.
        listener.onResponse(MLTrainingTaskResponse.builder().taskId(mlTask.getTaskId()).status(MLTaskState.CREATED.name()).build());
        if (request.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(dataFrame -> { train(mlTask, dataFrame, request); }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    mlTaskManager.addIfAbsent(mlTask);
                    mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
                    mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
                });
            mlInputDatasetHandler
                .parseSearchQueryInput(
                    request.getInputDataset(),
                    new ThreadedActionListener<>(log, threadPool, TASK_THREAD_POOL, dataFrameActionListener, false)
                );
        } else {
            DataFrame inputDataFrame = mlInputDatasetHandler.parseDataFrameInput(request.getInputDataset());
            threadPool.executor(TASK_THREAD_POOL).execute(() -> { train(mlTask, inputDataFrame, request); });
        }
    }

    private void train(MLTask mlTask, DataFrame inputDataFrame, MLTrainingTaskRequest request) {
        // track ML task count and add ML task into cache
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
        mlTaskManager.add(mlTask);

        // run training
        try {
            mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
            Model model = MLEngine.train(request.getAlgorithm(), request.getParameters(), inputDataFrame);
            String encodedModelContent = Base64.getEncoder().encodeToString(model.getContent());
            mlIndicesHandler.initModelIndexIfAbsent();
            Map<String, Object> source = new HashMap<>();
            source.put(TASK_ID, mlTask.getTaskId());
            source.put(ALGORITHM, request.getAlgorithm());
            source.put(MODEL_NAME, model.getName());
            source.put(MODEL_VERSION, model.getVersion());
            source.put(MODEL_CONTENT, encodedModelContent);
            IndexResponse response = client.prepareIndex(OS_ML_MODEL_RESULT, "_doc").setSource(source).get();
            log.info("mode data indexing done, result:{}", response.getResult());
            handleMLTaskComplete(mlTask);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error(e);
            handleMLTaskFailure(mlTask, e);
        }
    }
}
