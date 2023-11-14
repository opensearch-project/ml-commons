/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLSyncUpCron implements Runnable {

    public static final int DEPLOY_MODEL_TASK_GRACE_TIME_IN_MS = 20_000;
    private Client client;
    private ClusterService clusterService;
    private DiscoveryNodeHelper nodeHelper;
    private MLIndicesHandler mlIndicesHandler;
    private Encryptor encryptor;
    private volatile Boolean mlConfigInited;
    @VisibleForTesting
    Semaphore updateModelStateSemaphore;
    private MLModelAutoReDeployer mlModelAutoReDeployer;

    public MLSyncUpCron(
        Client client,
        ClusterService clusterService,
        DiscoveryNodeHelper nodeHelper,
        MLIndicesHandler mlIndicesHandler,
        Encryptor encryptor
    ) {
        this.client = client;
        this.clusterService = clusterService;
        this.nodeHelper = nodeHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.updateModelStateSemaphore = new Semaphore(1);
        this.mlConfigInited = false;
        this.encryptor = encryptor;
    }

    @Override
    public void run() {
        initMLConfig();
        if (!clusterService.state().metadata().indices().containsKey(ML_MODEL_INDEX)) {
            // no need to run sync up job if no model index
            return;
        }
        log.debug("ML sync job starts");
        DiscoveryNode[] allNodes = nodeHelper.getAllNodes();
        MLSyncUpInput gatherInfoInput = MLSyncUpInput.builder().getDeployedModels(true).build();
        MLSyncUpNodesRequest gatherInfoRequest = new MLSyncUpNodesRequest(allNodes, gatherInfoInput);

        // gather running model/tasks on nodes
        client.execute(MLSyncUpAction.INSTANCE, gatherInfoRequest, ActionListener.wrap(r -> {
            List<MLSyncUpNodeResponse> responses = r.getNodes();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
            // key is task id, value is set of worker node ids
            Map<String, Set<String>> runningDeployModelTasks = new HashMap<>();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> deployingModels = new HashMap<>();
            for (MLSyncUpNodeResponse response : responses) {
                String nodeId = response.getNode().getId();
                String[] deployedModelIds = response.getDeployedModelIds();
                if (deployedModelIds != null && deployedModelIds.length > 0) {
                    for (String modelId : deployedModelIds) {
                        Set<String> workerNodes = modelWorkerNodes.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
                String[] runningModelIds = response.getRunningDeployModelIds();
                if (runningModelIds != null && runningModelIds.length > 0) {
                    for (String modelId : runningModelIds) {
                        Set<String> workerNodes = deployingModels.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                } else {

                }

                String[] runningDeployModelTaskIds = response.getRunningDeployModelTaskIds();
                if (runningDeployModelTaskIds != null && runningDeployModelTaskIds.length > 0) {
                    for (String taskId : runningDeployModelTaskIds) {
                        Set<String> workerNodes = runningDeployModelTasks.computeIfAbsent(taskId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
            }
            for (Map.Entry<String, Set<String>> entry : modelWorkerNodes.entrySet()) {
                String modelId = entry.getKey();
                log.debug("will sync model worker nodes for model: {}: {}", modelId, entry.getValue().toArray(new String[0]));
            }
            for (Map.Entry<String, Set<String>> entry : runningDeployModelTasks.entrySet()) {
                log.debug("will sync running task: {}: {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            MLSyncUpInput.MLSyncUpInputBuilder inputBuilder = MLSyncUpInput
                .builder()
                .syncRunningDeployModelTasks(true)
                .runningDeployModelTasks(runningDeployModelTasks);
            if (modelWorkerNodes.size() == 0) {
                log.debug("No deployed model found. Will clear model routing on all nodes");
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
                    ActionListener.wrap(re -> { log.debug("sync model routing job finished"); }, ex -> {
                        log.error("Failed to sync model routing", ex);
                    })
                );

            // refresh model status
            mlIndicesHandler
                .initModelIndexIfAbsent(ActionListener.wrap(res -> { refreshModelState(modelWorkerNodes, deployingModels); }, e -> {
                    log.error("Failed to init model index", e);
                }));
        }, e -> { log.error("Failed to sync model routing", e); }));
    }

    @VisibleForTesting
    void initMLConfig() {
        if (mlConfigInited) {
            return;
        }
        mlIndicesHandler.initMLConfigIndex(ActionListener.wrap(r -> {
            GetRequest getRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(getRequest, ActionListener.wrap(getResponse -> {
                    if (!getResponse.isExists()) {
                        IndexRequest indexRequest = new IndexRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                        final String masterKey = encryptor.generateMasterKey();
                        indexRequest.source(ImmutableMap.of(MASTER_KEY, masterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()));
                        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
                            log.info("ML configuration initialized successfully");
                            encryptor.setMasterKey(masterKey);
                            mlConfigInited = true;
                        }, e -> { log.debug("Failed to save ML encryption master key", e); }));
                    } else {
                        final String masterKey = (String) getResponse.getSourceAsMap().get(MASTER_KEY);
                        encryptor.setMasterKey(masterKey);
                        mlConfigInited = true;
                        log.info("ML configuration already initialized, no action needed");
                    }
                }, e -> { log.debug("Failed to get ML encryption master key", e); }));
            }
        }, e -> { log.debug("Failed to init ML config index", e); }));
    }

    @VisibleForTesting
    void refreshModelState(Map<String, Set<String>> modelWorkerNodes, Map<String, Set<String>> deployingModels) {
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
                                MLModelState.LOAD_FAILED.name(),
                                MLModelState.DEPLOYING.name(),
                                MLModelState.PARTIALLY_DEPLOYED.name(),
                                MLModelState.DEPLOYED.name(),
                                MLModelState.DEPLOY_FAILED.name()
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
                        MLModel.ALGORITHM_FIELD,
                        MLModel.DEPLOY_TO_ALL_NODES_FIELD,
                        MLModel.PLANNING_WORKER_NODES_FIELD,
                        MLModel.PLANNING_WORKER_NODE_COUNT_FIELD,
                        MLModel.LAST_UPDATED_TIME_FIELD,
                        MLModel.CURRENT_WORKER_NODE_COUNT_FIELD },
                    null
                );
            searchRequest.source(sourceBuilder);
            client.search(searchRequest, ActionListener.wrap(res -> {
                SearchHit[] hits = res.getHits().getHits();
                Map<String, MLModelState> newModelStates = new HashMap<>();
                Map<String, List<String>> newPlanningWorkerNodes = new HashMap<>();
                for (SearchHit hit : hits) {
                    String modelId = hit.getId();
                    Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                    FunctionName functionName = FunctionName.from((String) sourceAsMap.get(MLModel.ALGORITHM_FIELD));
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
                    boolean deployToAllNodes = sourceAsMap.containsKey(MLModel.DEPLOY_TO_ALL_NODES_FIELD)
                        ? (boolean) sourceAsMap.get(MLModel.DEPLOY_TO_ALL_NODES_FIELD)
                        : false;
                    List<String> planningWorkNodes = sourceAsMap.containsKey(MLModel.PLANNING_WORKER_NODES_FIELD)
                        ? (List<String>) sourceAsMap.get(MLModel.PLANNING_WORKER_NODES_FIELD)
                        : new ArrayList<>();
                    if (deployToAllNodes) {
                        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(functionName);
                        planningWorkerNodeCount = eligibleNodes.length;
                        List<String> eligibleNodeIds = Arrays
                            .asList(eligibleNodes)
                            .stream()
                            .map(n -> n.getId())
                            .collect(Collectors.toList());
                        if (eligibleNodeIds.size() != planningWorkNodes.size() || !eligibleNodeIds.containsAll(planningWorkNodes)) {
                            newPlanningWorkerNodes.put(modelId, eligibleNodeIds);
                        }
                    }
                    if (modelWorkerNodes != null && modelWorkerNodes.size() != 0) {
                        MLModelState mlModelState = getNewModelState(
                            deployingModels,
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
                }
                bulkUpdateModelState(modelWorkerNodes, newModelStates, newPlanningWorkerNodes);
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
        Map<String, Set<String>> deployingModels,
        Map<String, Set<String>> modelWorkerNodes,
        String modelId,
        MLModelState state,
        Long lastUpdateTime,
        int planningWorkerNodeCount,
        int currentWorkerNodeCountInIndex
    ) {
        Set<String> deployModelTaskNodes = deployingModels.get(modelId);
        if (deployModelTaskNodes != null && deployModelTaskNodes.size() > 0 && state != MLModelState.DEPLOYING) {
            // If some node/nodes are deploying the model and model state is not DEPLOYING, then set model state as DEPLOYING.
            return MLModelState.DEPLOYING;
        }
        int currentWorkerNodeCount = modelWorkerNodes.containsKey(modelId) ? modelWorkerNodes.get(modelId).size() : 0;
        if (currentWorkerNodeCount == 0
            && state != MLModelState.DEPLOY_FAILED
            && !(state == MLModelState.DEPLOYING
                && lastUpdateTime != null
                && lastUpdateTime + DEPLOY_MODEL_TASK_GRACE_TIME_IN_MS > Instant.now().toEpochMilli())) {
            // If model not deployed to any node and no node is deploying the model, then set model state as DEPLOY_FAILED
            return MLModelState.DEPLOY_FAILED;
        }
        if (currentWorkerNodeCount > 0) {
            if (currentWorkerNodeCount < planningWorkerNodeCount
                && (state != MLModelState.PARTIALLY_DEPLOYED || currentWorkerNodeCountInIndex != currentWorkerNodeCount)) {
                // If model deployed to some node/nodes, but not deployed to all nodes planned by user,
                // then set model state as PARTIALLY_DEPLOYED.
                return MLModelState.PARTIALLY_DEPLOYED;
            } else if (planningWorkerNodeCount > 0 && currentWorkerNodeCount >= planningWorkerNodeCount && state != MLModelState.DEPLOYED) {
                if (currentWorkerNodeCount > planningWorkerNodeCount) {
                    // This case should not happen that model deployed to more nodes than planned. So log this as warning if
                    // it happens.
                    log
                        .warn(
                            "Model {} deployed on more nodes [{}] than planning worker node [{}]",
                            modelId,
                            currentWorkerNodeCount,
                            planningWorkerNodeCount
                        );
                }

                // If model deployed to all nodes planned by user, then set model state as DEPLOYED.
                return MLModelState.DEPLOYED;
            }
        }
        return null;
    }

    private void bulkUpdateModelState(
        Map<String, Set<String>> modelWorkerNodes,
        Map<String, MLModelState> newModelStates,
        Map<String, List<String>> newPlanningWorkNodes
    ) {
        Set<String> updatedModelIds = new HashSet<>();
        updatedModelIds.addAll(newModelStates.keySet());
        updatedModelIds.addAll(newPlanningWorkNodes.keySet());

        if (updatedModelIds.size() > 0) {
            BulkRequest bulkUpdateRequest = new BulkRequest();
            for (String modelId : updatedModelIds) {
                UpdateRequest updateRequest = new UpdateRequest();
                Instant now = Instant.now();
                ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
                if (newModelStates.containsKey(modelId)) {
                    builder.put(MLModel.MODEL_STATE_FIELD, newModelStates.get(modelId).name());
                }
                if (newPlanningWorkNodes.containsKey(modelId)) {
                    builder.put(MLModel.PLANNING_WORKER_NODES_FIELD, newPlanningWorkNodes.get(modelId));
                    builder.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, newPlanningWorkNodes.get(modelId).size());
                }
                builder.put(MLModel.LAST_UPDATED_TIME_FIELD, now.toEpochMilli());
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
