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

import com.google.common.collect.ImmutableSet;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.prediction.MLPredictionTaskRemoteExecutionAction;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.common.dataframe.DataFrame;
import org.opensearch.ml.common.dataframe.DataFrameBuilder;
import org.opensearch.ml.common.dataset.DataFrameInputDataset;
import org.opensearch.ml.common.dataset.MLInputDataType;
import org.opensearch.ml.common.dataset.MLInputDataset;
import org.opensearch.ml.common.dataset.SearchQueryInputDataset;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskResponse;
import org.opensearch.ml.common.transport.training.MLTrainingTaskRequest;
import org.opensearch.ml.common.transport.training.MLTrainingTaskResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.Model;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.utils.MLNodeUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;
import static org.opensearch.ml.stats.InternalStatNames.JVM_HEAP_USAGE;
import static org.opensearch.ml.indices.MLIndicesHandler.OS_ML_MODEL_RESULT;



import javax.naming.LimitExceededException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private volatile Integer maxAdBatchTaskPerNode;

    public MLTaskRunner(
            ThreadPool threadPool,
            ClusterService clusterService,
            Client client,
            MLTaskManager mlTaskManager,
            MLStats mlStats,
            MLIndicesHandler mlIndicesHandler
    ) {
        this.threadPool = threadPool;
        this.clusterService = clusterService;
        this.client = client;
        this.mlTaskManager = mlTaskManager;
        this.mlStats = mlStats;
        this.mlIndicesHandler = mlIndicesHandler;
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
    public void runPrediction(MLPredictionTaskRequest request, TransportService transportService, ActionListener<MLPredictionTaskResponse> listener) {
        dispatchTask(ActionListener.wrap(node -> {
            if (clusterService.localNode().getId().equals(node.getId())) {
                // Execute prediction task locally
                log
                        .info(
                                "execute ML prediction request {} locally on node {}",
                                request.toString(),
                                node.getId()
                        );
                startPredictionTask(request, listener);
            } else {
                // Execute batch task remotely
                log
                        .info(
                                "execute ML prediction request {} remotely on node {}",
                                request.toString(),
                                node.getId()
                        );
                transportService
                        .sendRequest(
                                node,
                                MLPredictionTaskRemoteExecutionAction.NAME,
                                MLPredictionTaskRequest.fromActionRequest(request),
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
    public void startPredictionTask(
            MLPredictionTaskRequest request,
            ActionListener<MLPredictionTaskResponse> listener
    ) {
        threadPool.executor(TASK_THREAD_POOL).execute(() -> {
            MLTask mlTask = MLTask.builder()
                    .taskId(UUID.randomUUID().toString())
                    .taskType(MLTaskType.PREDICTION)
                    .createTime(Instant.now())
                    .state(MLTaskState.CREATED)
                    .build();

            // track ML task count and add ML task into cache
            mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
            mlTaskManager.add(mlTask);

            // run prediction
            String modelData = null;
            if(request.getModelId() != null) {
                val response = client.prepareGet(OS_ML_MODEL_RESULT, "_doc", request.getModelId()).get();
                modelData = new String(Base64.getDecoder().decode(response.getSourceAsMap().get("model").toString()));
            }

            DataFrame forecastsResults = null;
            try {
                mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
                // todo: MLEngine is still implementing, put a placeholder here
                forecastsResults = MLEngine.predict(request.getAlgorithm(), request.getParameters(), request.getDataFrame(), modelData);

                // Once prediction complete, reduce ML_EXECUTING_TASK_COUNT and update task state
                mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
                mlTask.setState(MLTaskState.COMPLETED);
            } catch (Exception e) { // todo need to specify what exception
                log.error(e);
                mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
                mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
                mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
                listener.onFailure(e);
            }

            MLPredictionTaskResponse response = MLPredictionTaskResponse.builder()
                    .taskId(mlTask.getTaskId())
                    .status(mlTaskManager.get(mlTask.getTaskId()).getState().name())
                    .predictionResult(forecastsResults).build();
            listener.onResponse(response);
        });
    }


    /**
     * Start training task
     * @param request MLTrainingTaskRequest
     * @param listener Action listener
     */
    public void startTrainingTask(
            MLTrainingTaskRequest request,
            ActionListener<MLTrainingTaskResponse> listener
    ) {
        String taskId = UUID.randomUUID().toString();
        // if searchquery
        // search(request, listener(thread pool listener))
        // if dataframe

        threadPool.executor(TASK_THREAD_POOL).execute(() -> {
            MLTask mlTask = MLTask.builder()
                    .taskId(taskId)
                    .taskType(MLTaskType.TRAINING)
                    .createTime(Instant.now())
                    .state(MLTaskState.CREATED)
                    .build();

            // track ML task count and add ML task into cache
            mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).increment();
            mlTaskManager.add(mlTask);

            // run training
            try {
                mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
                // todo: MLEngine is not ready yet, put a placeholder here
                MLInputDataset mlInputDataset = request.getInputDataset();
                DataFrame inputDataFrame = null;
                if (mlInputDataset.getInputDataType() == MLInputDataType.DATA_FRAME) {
                    DataFrameInputDataset inputDataset = (DataFrameInputDataset) mlInputDataset;
                    inputDataFrame = inputDataset.getDataFrame();
                }
                Model model = MLEngine.train(request.getAlgorithm(), request.getParameters(), inputDataFrame);
                mlIndicesHandler.initModelIndexIfAbsent();
                val source = new HashMap<String, Object>();
                source.put("taskId", taskId);
                source.put("algorithm", request.getAlgorithm());
                source.put("model", model.getContent());
                val response =
                        client.prepareIndex(OS_ML_MODEL_RESULT, "_doc").setSource(source).get();
                log.info("mode data indexing done, result:{}", response.getResult());
                mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
                mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.COMPLETED);
            } catch (Exception e) { // todo need to specify what exception
                log.error(e);
                mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
                mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
                mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
            }
        });

        listener.onResponse(MLTrainingTaskResponse.builder()
                .taskId(taskId)
                .status(MLTaskState.CREATED.name())
                .build());
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
