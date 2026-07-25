/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.CommonValue.CREATE_TIME_FIELD;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;
import static org.opensearch.ml.common.CommonValue.ML_CONFIG_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.getAllNodes;

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

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermsQueryBuilder;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelsRequest;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.remote.metadata.client.BulkDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLSyncUpCron implements Runnable {

    public static final int DEPLOY_MODEL_TASK_GRACE_TIME_IN_MS = 20_000;
    private Client client;
    private final SdkClient sdkClient;
    private ClusterService clusterService;
    private DiscoveryNodeHelper nodeHelper;
    private MLIndicesHandler mlIndicesHandler;
    private Encryptor encryptor;
    private volatile Boolean mlConfigInited;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @VisibleForTesting
    Semaphore updateModelStateSemaphore;

    public MLSyncUpCron(
        Client client,
        SdkClient sdkClient,
        ClusterService clusterService,
        DiscoveryNodeHelper nodeHelper,
        MLIndicesHandler mlIndicesHandler,
        Encryptor encryptor,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.clusterService = clusterService;
        this.nodeHelper = nodeHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.updateModelStateSemaphore = new Semaphore(1);
        this.mlConfigInited = false;
        this.encryptor = encryptor;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
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
            if (r.failures() != null && !r.failures().isEmpty()) {
                log
                    .debug(
                        "Received {} failures in the sync up response on nodes. Error messages are {}",
                        r.failures().size(),
                        r.failures().stream().map(Exception::getMessage).collect(Collectors.joining(", "))
                    );
            }
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> modelWorkerNodes = new HashMap<>();
            // key is task id, value is set of worker node ids
            Map<String, Set<String>> runningDeployModelTasks = new HashMap<>();
            // key is model id, value is set of worker node ids
            Map<String, Set<String>> deployingModels = new HashMap<>();
            // key is expired model_id, value is set of worker node ids
            Map<String, Set<String>> expiredModelToNodes = new HashMap<>();
            for (MLSyncUpNodeResponse response : responses) {
                String nodeId = response.getNode().getId();
                String[] expiredModelIds = response.getExpiredModelIds();
                if (expiredModelIds != null && expiredModelIds.length > 0) {
                    Arrays
                        .stream(expiredModelIds)
                        .forEach(modelId -> { expiredModelToNodes.computeIfAbsent(modelId, it -> new HashSet<>()).add(nodeId); });
                }

                String[] deployedModelIds = response.getDeployedModelIds();
                if (deployedModelIds != null) {
                    for (String modelId : deployedModelIds) {
                        Set<String> workerNodes = modelWorkerNodes.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
                String[] runningModelIds = response.getRunningDeployModelIds();
                if (runningModelIds != null) {
                    for (String modelId : runningModelIds) {
                        Set<String> workerNodes = deployingModels.computeIfAbsent(modelId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }

                String[] runningDeployModelTaskIds = response.getRunningDeployModelTaskIds();
                if (runningDeployModelTaskIds != null) {
                    for (String taskId : runningDeployModelTaskIds) {
                        Set<String> workerNodes = runningDeployModelTasks.computeIfAbsent(taskId, it -> new HashSet<>());
                        workerNodes.add(nodeId);
                    }
                }
            }

            Set<String> modelsToUndeploy = new HashSet<>();
            for (String modelId : expiredModelToNodes.keySet()) {
                if (modelWorkerNodes.containsKey(modelId)
                    && expiredModelToNodes.get(modelId).size() == modelWorkerNodes.get(modelId).size()) {
                    // this model has expired in all the nodes
                    modelsToUndeploy.add(modelId);
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
            if (modelWorkerNodes.isEmpty()) {
                log.debug("No deployed model found. Will clear model routing on all nodes");
                inputBuilder.clearRoutingTable(true);
            } else {
                inputBuilder.modelRoutingTable(modelWorkerNodes);
            }
            MLSyncUpInput syncUpInput = inputBuilder.build();
            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(allNodes, syncUpInput);
            // sync up running model/tasks on nodes
            client.execute(MLSyncUpAction.INSTANCE, syncUpRequest, ActionListener.wrap(re -> {
                log.debug("sync model routing job finished");
                if (!modelsToUndeploy.isEmpty()) {
                    // Undeploy expired models
                    undeployExpiredModels(modelsToUndeploy, modelWorkerNodes, deployingModels);
                    return;
                }
                // refresh model status
                mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                    if (!res) {
                        log.error("No response to create ML model index");
                        return;
                    }
                    refreshModelState(modelWorkerNodes, deployingModels);
                }, e -> { log.error("Failed to init model index", e); }));
            }, ex -> { log.error("Failed to sync model routing", ex); }));
        }, e -> { log.error("Failed to sync model routing", e); }));
    }

    private void undeployExpiredModels(
        Set<String> expiredModels,
        Map<String, Set<String>> modelWorkerNodes,
        Map<String, Set<String>> deployingModels
    ) {
        String[] targetNodeIds = getAllNodes(clusterService);
        MLUndeployModelsRequest mlUndeployModelsRequest = new MLUndeployModelsRequest(
            expiredModels.toArray(new String[expiredModels.size()]),
            targetNodeIds,
            null
        );

        client.execute(MLUndeployModelsAction.INSTANCE, mlUndeployModelsRequest, ActionListener.wrap(r -> {
            MLUndeployModelNodesResponse mlUndeployModelNodesResponse = r.getResponse();
            if (mlUndeployModelNodesResponse.failures() != null && !mlUndeployModelNodesResponse.failures().isEmpty()) {
                log.debug("Received failures in undeploying expired models", mlUndeployModelNodesResponse.failures());
            }

            mlIndicesHandler.initModelIndexIfAbsent(ActionListener.wrap(res -> {
                if (!res) {
                    log.error("No response to create ML model index");
                    return;
                }
                refreshModelState(modelWorkerNodes, deployingModels);
            }, e -> { log.error("Failed to init model index", e); }));
        }, e -> { log.error("Failed to undeploy models {}", expiredModels, e); }));
    }

    @VisibleForTesting
    void initMLConfig() {
        if (mlConfigInited || mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
            return;
        }
        mlIndicesHandler.initMLConfigIndex(ActionListener.wrap(r -> {
            if (!r) {
                log.error("Failed to initialize or update ML Config index");
                return;
            }
            GetRequest getRequest = new GetRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(getRequest, ActionListener.wrap(getResponse -> {
                    if (!getResponse.isExists()) {
                        IndexRequest indexRequest = new IndexRequest(ML_CONFIG_INDEX).id(MASTER_KEY);
                        final String masterKey = encryptor.generateMasterKey();
                        indexRequest.source(ImmutableMap.of(MASTER_KEY, masterKey, CREATE_TIME_FIELD, Instant.now().toEpochMilli()));
                        indexRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                        indexRequest.opType(DocWriteRequest.OpType.CREATE);
                        client.index(indexRequest, ActionListener.wrap(indexResponse -> {
                            log.info("ML configuration initialized successfully");
                            // as this method is not being used for multi-tenancy use case, we are setting
                            // tenant id null by default
                            encryptor.setMasterKey(null, masterKey);
                            mlConfigInited = true;
                        }, e -> { log.debug("Failed to save ML encryption master key", e); }));
                    } else {
                        final String masterKey = (String) getResponse.getSourceAsMap().get(MASTER_KEY);
                        // as this method is not being used for multi-tenancy use case, we are setting
                        // tenant id null by default
                        encryptor.setMasterKey(null, masterKey);
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
                        CommonValue.TENANT_ID_FIELD,
                        MLModel.MODEL_STATE_FIELD,
                        MLModel.ALGORITHM_FIELD,
                        MLModel.DEPLOY_TO_ALL_NODES_FIELD,
                        MLModel.PLANNING_WORKER_NODES_FIELD,
                        MLModel.PLANNING_WORKER_NODE_COUNT_FIELD,
                        MLModel.LAST_UPDATED_TIME_FIELD,
                        MLModel.CURRENT_WORKER_NODE_COUNT_FIELD },
                    null
                );
            SearchDataObjectRequest searchRequest = SearchDataObjectRequest
                .builder()
                .indices(ML_MODEL_INDEX)
                .searchSourceBuilder(sourceBuilder)
                .build();
            sdkClient.searchDataObjectAsync(searchRequest).whenComplete((r, throwable) -> {
                if (throwable == null) {
                    try {
                        SearchResponse res = r.searchResponse();
                        // Parsing failure would cause NPE on next line
                        SearchHit[] hits = res.getHits().getHits();
                        Map<String, String> tenantIds = new HashMap<>();
                        Map<String, MLModelState> newModelStates = new HashMap<>();
                        Map<String, List<String>> newPlanningWorkerNodes = new HashMap<>();
                        for (SearchHit hit : hits) {
                            String modelId = hit.getId();
                            Map<String, Object> sourceAsMap = hit.getSourceAsMap();
                            if (sourceAsMap.containsKey(CommonValue.TENANT_ID_FIELD)) {
                                tenantIds.put(modelId, (String) sourceAsMap.get(CommonValue.TENANT_ID_FIELD));
                            }
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
                                && (boolean) sourceAsMap.get(MLModel.DEPLOY_TO_ALL_NODES_FIELD);
                            List<String> planningWorkNodes = sourceAsMap.containsKey(MLModel.PLANNING_WORKER_NODES_FIELD)
                                ? (List<String>) sourceAsMap.get(MLModel.PLANNING_WORKER_NODES_FIELD)
                                : new ArrayList<>();
                            if (deployToAllNodes) {
                                DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(functionName);
                                planningWorkerNodeCount = eligibleNodes.length;
                                List<String> eligibleNodeIds = Arrays
                                    .stream(eligibleNodes)
                                    .map(DiscoveryNode::getId)
                                    .collect(Collectors.toList());
                                if (eligibleNodeIds.size() != planningWorkNodes.size() || !eligibleNodeIds.containsAll(planningWorkNodes)) {
                                    newPlanningWorkerNodes.put(modelId, eligibleNodeIds);
                                }
                            }
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
                        bulkUpdateModelState(modelWorkerNodes, newModelStates, newPlanningWorkerNodes, tenantIds);
                    } catch (Exception e) {
                        log.error("Failed to parse model search response", e);
                        updateModelStateSemaphore.release();
                    }
                } else {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
                    updateModelStateSemaphore.release();
                    log.error("Failed to search models", e);
                }
            });
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
        if (deployModelTaskNodes != null && !deployModelTaskNodes.isEmpty() && state != MLModelState.DEPLOYING) {
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
        Map<String, List<String>> newPlanningWorkNodes,
        Map<String, String> tenantIds
    ) {
        Set<String> updatedModelIds = new HashSet<>();
        updatedModelIds.addAll(newModelStates.keySet());
        updatedModelIds.addAll(newPlanningWorkNodes.keySet());

        if (!updatedModelIds.isEmpty()) {
            BulkDataObjectRequest bulkUpdateRequest = BulkDataObjectRequest.builder().globalIndex(ML_MODEL_INDEX).build();
            for (String modelId : updatedModelIds) {
                Instant now = Instant.now();
                Map<String, Object> updateDocument = new HashMap<>();
                if (newModelStates.containsKey(modelId)) {
                    updateDocument.put(MLModel.MODEL_STATE_FIELD, newModelStates.get(modelId).name());
                }
                if (newPlanningWorkNodes.containsKey(modelId)) {
                    updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, newPlanningWorkNodes.get(modelId));
                    updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, newPlanningWorkNodes.get(modelId).size());
                }
                updateDocument.put(MLModel.LAST_UPDATED_TIME_FIELD, now.toEpochMilli());
                Set<String> workerNodes = modelWorkerNodes.get(modelId);
                int currentWorkNodeCount = workerNodes == null ? 0 : workerNodes.size();
                updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, currentWorkNodeCount);
                UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
                    .builder()
                    .tenantId(tenantIds.get(modelId))
                    .id(modelId)
                    .dataObject(updateDocument)
                    .build();
                bulkUpdateRequest.add(updateRequest);
            }
            bulkUpdateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            log.info("Refresh model state: {}", newModelStates);
            sdkClient.bulkDataObjectAsync(bulkUpdateRequest).whenComplete((r, throwable) -> {
                updateModelStateSemaphore.release();
                if (throwable != null) {
                    Exception e = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
                    log.error("Failed to bulk update model state", e);
                } else {
                    log.debug("Refresh model state successfully");
                }
            });
        } else {
            updateModelStateSemaphore.release();
        }
    }
}
