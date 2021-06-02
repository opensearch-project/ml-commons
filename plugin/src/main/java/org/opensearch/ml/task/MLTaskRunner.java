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
import static org.opensearch.ml.stats.InternalStatNames.JVM_HEAP_USAGE;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.naming.LimitExceededException;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ThreadedActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.prediction.MLPredictionTaskExecutionAction;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.action.training.MLTrainingTaskExecutionAction;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
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
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableSet;

/**
 * MLTaskRunner is responsible for dispatching and running predict/training tasks.
 */
@Log4j2
public class MLTaskRunner {
    // todo: move to a config class
    private final short DEFAULT_JVM_HEAP_USAGE_THRESHOLD = 85;
    private final ThreadPool threadPool;
    private final ClusterService clusterService;
    private final Client client;
    private final MLTaskManager mlTaskManager;
    private final MLStats mlStats;
    private final MLIndicesHandler mlIndicesHandler;
    private final MLInputDatasetHandler mlInputDatasetHandler;
    private volatile Integer maxAdBatchTaskPerNode;

    public MLTaskRunner(
        ThreadPool threadPool,
        ClusterService clusterService,
        Client client,
        MLTaskManager mlTaskManager,
        MLStats mlStats,
        MLIndicesHandler mlIndicesHandler,
        MLInputDatasetHandler mlInputDatasetHandler
    ) {
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlTaskManager = mlTaskManager;
        this.mlStats = mlStats;
        this.mlIndicesHandler = mlIndicesHandler;
        this.mlInputDatasetHandler = mlInputDatasetHandler;
        this.maxAdBatchTaskPerNode = MLTaskManager.MAX_ML_TASK_PER_NODE;
    }

    /**
     * Select least loaded node based on ML_EXECUTING_TASK_COUNT and JVM_HEAP_USAGE
     * @param listener Action listener
     */
    public void dispatchTask(ActionListener<DiscoveryNode> listener) {
        // todo: add ML node type setting check
        DiscoveryNode[] mlNodes = getEligibleMLNodes();
        MLStatsNodesRequest MLStatsNodesRequest = new MLStatsNodesRequest(mlNodes);
        MLStatsNodesRequest.addAll(ImmutableSet.of(ML_EXECUTING_TASK_COUNT.getName(), JVM_HEAP_USAGE.getName()));

        client.execute(MLStatsNodesAction.INSTANCE, MLStatsNodesRequest, ActionListener.wrap(mlStatsResponse -> {
            // Check JVM pressure
            List<MLStatsNodeResponse> candidateNodeResponse = mlStatsResponse
                .getNodes()
                .stream()
                .filter(stat -> (long) stat.getStatsMap().get(JVM_HEAP_USAGE.getName()) < DEFAULT_JVM_HEAP_USAGE_THRESHOLD)
                .collect(Collectors.toList());

            if (candidateNodeResponse.size() == 0) {
                String errorMessage = "All nodes' memory usage exceeds limitation"
                    + DEFAULT_JVM_HEAP_USAGE_THRESHOLD
                    + ". No eligible node to run ml jobs ";
                log.warn(errorMessage);
                listener.onFailure(new LimitExceededException(errorMessage));
                return;
            }

            // Check # of executing ML task
            candidateNodeResponse = candidateNodeResponse
                .stream()
                .filter(stat -> (Long) stat.getStatsMap().get(ML_EXECUTING_TASK_COUNT.getName()) < maxAdBatchTaskPerNode)
                .collect(Collectors.toList());
            if (candidateNodeResponse.size() == 0) {
                String errorMessage = "All nodes' executing ML task count exceeds limitation.";
                log.warn(errorMessage);
                listener.onFailure(new LimitExceededException(errorMessage));
                return;
            }

            // sort nodes by JVM usage percentage and # of executing ML task
            Optional<MLStatsNodeResponse> targetNode = candidateNodeResponse
                .stream()
                .sorted((MLStatsNodeResponse r1, MLStatsNodeResponse r2) -> {
                    int result = ((Long) r1.getStatsMap().get(ML_EXECUTING_TASK_COUNT.getName()))
                        .compareTo((Long) r2.getStatsMap().get(ML_EXECUTING_TASK_COUNT.getName()));
                    if (result == 0) {
                        // if multiple nodes have same running task count, choose the one with least
                        // JVM heap usage.
                        return ((Long) r1.getStatsMap().get(JVM_HEAP_USAGE.getName()))
                            .compareTo((Long) r2.getStatsMap().get(JVM_HEAP_USAGE.getName()));
                    }
                    return result;
                })
                .findFirst();
            listener.onResponse(targetNode.get().getNode());
        }, exception -> {
            log.error("Failed to get node's task stats", exception);
            listener.onFailure(exception);
        }));
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
        dispatchTask(ActionListener.wrap(node -> {
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
                    mlTaskManager.add(mlTask);
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

        // get model
        Model model = new Model();
        if (request.getModelId() != null) {
            GetResponse response = client.prepareGet(OS_ML_MODEL_RESULT, "_doc", request.getModelId()).get();
            Map<String, Object> source = response.getSourceAsMap();
            model.setName((String) source.get("modelName"));
            model.setVersion(Integer.parseInt((String) source.get("modelVersion")));
            byte[] decoded = Base64.getDecoder().decode(source.get("modelContent").toString().getBytes());
            model.setContent(decoded);
        } else {
            IllegalArgumentException e = new IllegalArgumentException("ModelId is invalid");
            handleMLTaskFailure(mlTask, e);
            log.error("ModelId is invalid", e);
            listener.onFailure(e);
            return;
        }

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
        dispatchTask(ActionListener.wrap(node -> {
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
        listener.onResponse(MLTrainingTaskResponse.builder().taskId(mlTask.getTaskId()).status(MLTaskState.CREATED.name()).build());
        if (request.getInputDataset().getInputDataType().equals(MLInputDataType.SEARCH_QUERY)) {
            ActionListener<DataFrame> dataFrameActionListener = ActionListener
                .wrap(dataFrame -> { train(mlTask, dataFrame, request); }, e -> {
                    log.error("Failed to generate DataFrame from search query", e);
                    mlTaskManager.add(mlTask);
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
            byte[] encoded = Base64.getEncoder().encode(model.getContent());
            mlIndicesHandler.initModelIndexIfAbsent();
            Map<String, Object> source = new HashMap<>();
            source.put("taskId", mlTask.getTaskId());
            source.put("algorithm", request.getAlgorithm());
            source.put("modelName", model.getName());
            source.put("modelVersion", model.getVersion());
            source.put("modelContent", encoded);
            IndexResponse response = client.prepareIndex(OS_ML_MODEL_RESULT, "_doc").setSource(source).get();
            log.info("mode data indexing done, result:{}", response.getResult());
            handleMLTaskComplete(mlTask);
        } catch (Exception e) {
            // todo need to specify what exception
            log.error(e);
            handleMLTaskFailure(mlTask, e);
        }
    }

    private void handleMLTaskFailure(MLTask mlTask, Exception e) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.FAILED
        // update task error
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
        mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
    }

    private void handleMLTaskComplete(MLTask mlTask) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.COMPLETED
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.COMPLETED);
    }

    private DiscoveryNode[] getEligibleMLNodes() {
        ClusterState state = this.clusterService.state();
        final List<DiscoveryNode> eligibleNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            if (MLNodeUtils.isMLNode(node)) {
                eligibleNodes.add(node);
            }
        }
        return eligibleNodes.toArray(new DiscoveryNode[0]);
    }
}
