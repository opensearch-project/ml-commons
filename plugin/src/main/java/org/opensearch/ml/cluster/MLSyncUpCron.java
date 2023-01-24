/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

@Log4j2
public class MLSyncUpCron implements Runnable {

    public static final int LOAD_MODEL_TASK_GRACE_TIME_IN_MS = 20_000;
    private Client client;
    private ClusterService clusterService;
    private DiscoveryNodeHelper nodeHelper;
    private MLIndicesHandler mlIndicesHandler;
    @VisibleForTesting
    Semaphore updateModelStateSemaphore;

    public MLSyncUpCron(Client client, ClusterService clusterService, DiscoveryNodeHelper nodeHelper, MLIndicesHandler mlIndicesHandler) {
        this.client = client;
        this.clusterService = clusterService;
        this.nodeHelper = nodeHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.updateModelStateSemaphore = new Semaphore(1);
    }

    @Override
    public void run() {
        log.debug("ML sync job starts");
        DiscoveryNode[] allNodes = nodeHelper.getAllNodes();
        MLSyncUpInput gatherInfoInput = MLSyncUpInput.builder().getLoadedModels(true).build();
        MLSyncUpNodesRequest gatherInfoRequest = new MLSyncUpNodesRequest(allNodes, gatherInfoInput);
        // gather running model/tasks on nodes
        client.execute(MLSyncUpAction.INSTANCE, gatherInfoRequest, ActionListener.wrap(r -> {
            List<MLSyncUpNodeResponse> responses = r.getNodes();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
            // key is task id, value is set of worker node ids
            Map<String, Set<String>> runningLoadModelTasks = new HashMap<>();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> loadingModels = new HashMap<>();
            for (MLSyncUpNodeResponse response : responses) {
                String nodeId = response.getNode().getId();
                String[] loadedModelIds = response.getLoadedModelIds();
                if (loadedModelIds != null && loadedModelIds.length > 0) {
                    for (String modelId : loadedModelIds) {
                        Set<String> workerNodes = modelWorkerNodes.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
                String[] runningModelIds = response.getRunningLoadModelIds();
                if (runningModelIds != null && runningModelIds.length > 0) {
                    for (String modelId : runningModelIds) {
                        Set<String> workerNodes = loadingModels.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }

                String[] runningLoadModelTaskIds = response.getRunningLoadModelTaskIds();
                if (runningLoadModelTaskIds != null && runningLoadModelTaskIds.length > 0) {
                    for (String taskId : runningLoadModelTaskIds) {
                        Set<String> workerNodes = runningLoadModelTasks.computeIfAbsent(taskId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> entry : modelWorkerNodes.entrySet()) {
                String modelId = entry.getKey();
                log.debug("will sync model worker nodes for model: {}: {}", modelId, entry.getValue().toArray(new String[0]));
            }
            for (Map.Entry<String, Set<String>> entry : runningLoadModelTasks.entrySet()) {
                log.debug("will sync running task: {}: {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            MLSyncUpInput.MLSyncUpInputBuilder inputBuilder = MLSyncUpInput
                .builder()
                .syncRunningLoadModelTasks(true)
                .runningLoadModelTasks(runningLoadModelTasks);
            if (modelWorkerNodes.size() == 0) {
                log.debug("No loaded model found. Will clear model routing on all nodes");
                inputBuilder.clearRoutingTable(true);
            } else {
                inputBuilder.modelRoutingTable(modelWorkerNodes);
            }
            MLSyncUpInput syncUpInput = inputBuilder.build();
            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
            // sync up running model/tasks on nodes
            client
                .execute(
                    MLSyncUpAction.INSTANCE,
                    syncUpRequest,
                    ActionListener
                        .wrap(
                            re -> { log.debug("sync model routing job finished"); },
                            ex -> { log.error("Failed to sync model routing", ex); }
                        )
                );

            // refresh model status
            if (clusterService.state().getRoutingTable().hasIndex(ML_MODEL_INDEX)) {
                mlIndicesHandler
                    .initModelIndexIfAbsent(
                        ActionListener
                            .wrap(
                                res -> { refreshModelState(modelWorkerNodes, loadingModels); },
                                e -> { log.error("Failed to init model index", e); }
                            )
                    );
            }
        }, e -> { log.error("Failed to sync model routing", e); }));
    }

    @VisibleForTesting
    void refreshModelState(Map<String, Set<String>> modelWorkerNodes, Map<String, Set<String>> loadingModels) {
        if (!updateModelStateSemaphore.tryAcquire()) {
            return;
        }
        try {
            SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
            BoolQueryBuilder queryBuilder = new BoolQueryBuilder();
            queryBuilder
                .filter(
                    new TermsQueryBuilder(
                        MLModel.MODEL_STATE_FIELD,
                        Arrays
                            .asList(
                                MLModelState.LOADING.name(),
                                MLModelState.PARTIALLY_LOADED.name(),
                                MLModelState.LOADED.name(),
                                MLModelState.LOAD_FAILED.name()
                            )
                    )
                );
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(queryBuilder);
            sourceBuilder.size(10_000);
            sourceBuilder
                .fetchSource(
                    new String[] {
                        MLModel.MODEL_STATE_FIELD,
                        MLModel.PLANNING_WORKER_NODE_COUNT_FIELD,
                        MLModel.LAST_UPDATED_TIME_FIELD,
                        MLModel.CURRENT_WORKER_NODE_COUNT_FIELD },
                    null
                );
            searchRequest.source(sourceBuilder);
            client.search(searchRequest, ActionListener.wrap(res -> {
                SearchHit[] hits = res.getHits().getHits();
                Map<String, MLModelState> newModelStates = new HashMap<>();
                for (SearchHit hit : hits) {
                    String modelId = hit.getId();
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    MLModelState state = MLModelState.from((String) sourceAsMap.get(MLModel.MODEL_STATE_FIELD));
                    Long lastUpdateTime = sourceAsMap.containsKey(MLModel.LAST_UPDATED_TIME_FIELD)
                        ? (Long) sourceAsMap.get(MLModel.LAST_UPDATED_TIME_FIELD)
                        : null;
                    int planningWorkerNodeCount = sourceAsMap.containsKey(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD)
                        ? (int) sourceAsMap.get(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD)
                        : 0;
                    int currentWorkerNodeCountInIndex = sourceAsMap.containsKey(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD)
                        ? (int) sourceAsMap.get(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD)
                        : 0;
                    MLModelState mlModelState = getNewModelState(
                        loadingModels,
                        modelWorkerNodes,
                        modelId,
                        state,
                        lastUpdateTime,
                        planningWorkerNodeCount,
                        currentWorkerNodeCountInIndex
                    );
                    if (mlModelState != null) {
                        newModelStates.put(modelId, mlModelState);
                    }
                }
                bulkUpdateModelState(modelWorkerNodes, newModelStates);
            }, e -> {
                updateModelStateSemaphore.release();
                log.error("Failed to search models", e);
            }));
        } catch (Exception e) {
            updateModelStateSemaphore.release();
            log.error("Failed to refresh model state", e);
        }
    }

    private MLModelState getNewModelState(
        Map<String, Set<String>> loadingModels,
        Map<String, Set<String>> modelWorkerNodes,

        String modelId,
        MLModelState state,
        Long lastUpdateTime,
        int planningWorkerNodeCount,
        int currentWorkerNodeCountInIndex
    ) {
        Set<String> loadTaskNodes = loadingModels.get(modelId);
        if (loadTaskNodes != null && loadTaskNodes.size() > 0 && state != MLModelState.LOADING) {
            // no
            return MLModelState.LOADING;
        }
        int currentWorkerNodeCount = modelWorkerNodes.containsKey(modelId) ? modelWorkerNodes.get(modelId).size() : 0;
        if (currentWorkerNodeCount == 0
            && state != MLModelState.LOAD_FAILED
            && !(state == MLModelState.LOADING
                && lastUpdateTime != null
                && lastUpdateTime + LOAD_MODEL_TASK_GRACE_TIME_IN_MS > Instant.now().toEpochMilli())) {
            // If model not deployed to any node and no node is loading the model, then set model state as LOAD_FAILED
            return MLModelState.LOAD_FAILED;
        }
        if (currentWorkerNodeCount > 0) {
            if (currentWorkerNodeCount < planningWorkerNodeCount
                && (state != MLModelState.PARTIALLY_LOADED || currentWorkerNodeCountInIndex != currentWorkerNodeCount)) {
                // If model deployed to some node/nodes, but not deployed to all nodes planned by user,
                // then set model state as PARTIALLY_LOADED.
                return MLModelState.PARTIALLY_LOADED;
            } else if (planningWorkerNodeCount > 0 && currentWorkerNodeCount >= planningWorkerNodeCount && state != MLModelState.LOADED) {
                if (currentWorkerNodeCount > planningWorkerNodeCount) {
                    // This case should not happen that model loaded to more nodes than planned. So log this as warning if
                    // it happens.
                    log
                        .warn(
                            "Model {} loaded on more nodes [{}] than planing worker node[{}]",
                            modelId,
                            currentWorkerNodeCount,
                            planningWorkerNodeCount
                        );
                }

                // If model deployed to all nodes planned by user, then set model state as LOADED.
                return MLModelState.LOADED;
            }
        }
        return null;
    }

    private void bulkUpdateModelState(Map<String, Set<String>> modelWorkerNodes, Map<String, MLModelState> newModelStates) {
        if (newModelStates.size() > 0) {
            BulkRequest bulkUpdateRequest = new BulkRequest();
            for (String modelId : newModelStates.keySet()) {
                UpdateRequest updateRequest = new UpdateRequest();
                Instant now = Instant.now();
                ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
                builder
                    .put(MLModel.MODEL_STATE_FIELD, newModelStates.get(modelId).name())
                    .put(MLModel.LAST_UPDATED_TIME_FIELD, now.toEpochMilli());
                Set<String> workerNodes = modelWorkerNodes.get(modelId);
                int currentWorkNodeCount = workerNodes == null ? 0 : workerNodes.size();
                builder.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, currentWorkNodeCount);
                updateRequest.index(ML_MODEL_INDEX).id(modelId).doc(builder.build());
                bulkUpdateRequest.add(updateRequest);
            }
            log.info("Refresh model state: {}", newModelStates);
            client.bulk(bulkUpdateRequest, ActionListener.wrap(br -> {
                updateModelStateSemaphore.release();
                log.debug("Refresh model state successfully");
            }, e -> {
                updateModelStateSemaphore.release();
                log.error("Failed to bulk update model state", e);
            }));
        } else {
            updateModelStateSemaphore.release();
        }
    }
}
