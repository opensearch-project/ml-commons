/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.UNDEPLOYED;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkItemResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelAction;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodeResponse;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesRequest;
import org.opensearch.ml.common.transport.undeploy.MLUndeployModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.remote.metadata.client.BulkDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableList;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUndeployModelAction extends
    TransportNodesAction<MLUndeployModelNodesRequest, MLUndeployModelNodesResponse, MLUndeployModelNodeRequest, MLUndeployModelNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private final SdkClient sdkClient;
    private final DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;

    @Inject
    public TransportUndeployModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        SdkClient sdkClient,
        DiscoveryNodeHelper nodeFilter,
        MLStats mlStats
    ) {
        super(
            MLUndeployModelAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLUndeployModelNodesRequest::new,
            MLUndeployModelNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLUndeployModelNodeResponse.class
        );
        this.mlModelManager = mlModelManager;

        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.nodeFilter = nodeFilter;
        this.mlStats = mlStats;
    }

    @Override
    protected void doExecute(Task task, MLUndeployModelNodesRequest request, ActionListener<MLUndeployModelNodesResponse> listener) {
        ActionListener<MLUndeployModelNodesResponse> wrappedListener = ActionListener.wrap(undeployModelNodesResponse -> {
            processUndeployModelResponseAndUpdate(request.getTenantId(), undeployModelNodesResponse, listener);
        }, listener::onFailure);
        super.doExecute(task, request, wrappedListener);
    }

    void processUndeployModelResponseAndUpdate(
        String tenantId,
        MLUndeployModelNodesResponse undeployModelNodesResponse,
        ActionListener<MLUndeployModelNodesResponse> listener
    ) {
        List<MLUndeployModelNodeResponse> responses = undeployModelNodesResponse.getNodes();
        if (responses == null || responses.isEmpty()) {
            listener.onResponse(undeployModelNodesResponse);
            return;
        }

        Map<String, List<String>> actualRemovedNodesMap = new HashMap<>();
        Map<String, String[]> modelWorkNodesBeforeRemoval = new HashMap<>();
        responses.forEach(r -> {
            Map<String, String[]> nodeCounts = r.getModelWorkerNodeBeforeRemoval();

            if (nodeCounts != null) {
                for (Map.Entry<String, String[]> entry : nodeCounts.entrySet()) {
                    // when undeploy an undeployed model, the entry.getvalue() is null
                    if (entry.getValue() != null
                        && (!modelWorkNodesBeforeRemoval.containsKey(entry.getKey())
                            || modelWorkNodesBeforeRemoval.get(entry.getKey()).length < entry.getValue().length)) {
                        modelWorkNodesBeforeRemoval.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            Map<String, String> modelUndeployStatus = r.getModelUndeployStatus();
            for (Map.Entry<String, String> entry : modelUndeployStatus.entrySet()) {
                String status = entry.getValue();
                if (UNDEPLOYED.equals(status)) {
                    String modelId = entry.getKey();
                    if (!actualRemovedNodesMap.containsKey(modelId)) {
                        actualRemovedNodesMap.put(modelId, new ArrayList<>());
                    }
                    actualRemovedNodesMap.get(modelId).add(r.getNode().getId());
                }
            }
        });

        MLSyncUpInput syncUpInput = MLSyncUpInput
            .builder()
            .removedWorkerNodes(covertRemoveNodesMapForSyncUp(actualRemovedNodesMap))
            .build();

        MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(nodeFilter.getAllNodes(), syncUpInput);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            if (!actualRemovedNodesMap.isEmpty()) {
                BulkDataObjectRequest bulkRequest = BulkDataObjectRequest.builder().globalIndex(ML_MODEL_INDEX).build();
                Map<String, Boolean> deployToAllNodes = new HashMap<>();
                for (String modelId : actualRemovedNodesMap.keySet()) {
                    List<String> removedNodes = actualRemovedNodesMap.get(modelId);
                    int removedNodeCount = removedNodes.size();
                    /**
                     *  If allow custom deploy is false, user can only undeploy all nodes and status is undeployed.
                     *  If allow custom deploy is true, user can undeploy all nodes and status is undeployed,
                     *  or undeploy partial nodes, and status is deployed, this case means user created a new deployment plan, and
                     *  we need to update both planning worker nodes (count) and current worker nodes (count)
                     *  and deployToAllNodes value in model index.
                     */
                    Map<String, Object> updateDocument = new HashMap<>();
                    if (modelWorkNodesBeforeRemoval.get(modelId).length == removedNodeCount) { // undeploy all nodes.
                        updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, ImmutableList.of());
                        updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, 0);
                        updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, 0);
                        updateDocument.put(MLModel.MODEL_STATE_FIELD, MLModelState.UNDEPLOYED);
                    } else { // undeploy partial nodes.
                        // TODO (to fix) when undeploy partial nodes, the original model status could be partially_deployed,
                        // and the user could be undeploying not running model nodes, and we should update model status to deployed.
                        updateDocument.put(MLModel.DEPLOY_TO_ALL_NODES_FIELD, false);
                        List<String> newPlanningWorkerNodes = Arrays
                            .stream(modelWorkNodesBeforeRemoval.get(modelId))
                            .filter(x -> !removedNodes.contains(x))
                            .collect(Collectors.toList());
                        updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, newPlanningWorkerNodes);
                        updateDocument.put(MLModel.PLANNING_WORKER_NODE_COUNT_FIELD, newPlanningWorkerNodes.size());
                        updateDocument.put(MLModel.CURRENT_WORKER_NODE_COUNT_FIELD, newPlanningWorkerNodes.size());
                        deployToAllNodes.put(modelId, false);
                    }

                    UpdateDataObjectRequest updateRequest = UpdateDataObjectRequest
                        .builder()
                        .id(modelId)
                        .tenantId(tenantId)
                        .dataObject(updateDocument)
                        .build();
                    bulkRequest.add(updateRequest).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                }
                syncUpInput.setDeployToAllNodes(deployToAllNodes);
                ActionListener<BulkResponse> actionListener = ActionListener.wrap(r -> {
                    log
                        .debug(
                            "updated model state as undeployed for : {}",
                            Arrays.toString(actualRemovedNodesMap.keySet().toArray(new String[0]))
                        );
                }, e -> { log.error("Failed to update model state as undeployed", e); });
                ActionListener<BulkResponse> wrappedListener = ActionListener.runAfter(actionListener, () -> {
                    syncUpUndeployedModels(syncUpRequest);
                    listener.onResponse(undeployModelNodesResponse);
                });
                sdkClient.bulkDataObjectAsync(bulkRequest).whenComplete((r, throwable) -> {
                    if (throwable != null) {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable, OpenSearchStatusException.class);
                        log.error("Failed to execute BulkDataObject request", cause);
                        wrappedListener.onFailure(cause);
                    } else {
                        try {
                            BulkResponse bulkResponse = r.bulkResponse();
                            log
                                .info(
                                    "Executed {} bulk operations with {} failures, Took: {}",
                                    bulkResponse.getItems().length,
                                    bulkResponse.hasFailures()
                                        ? Arrays.stream(bulkResponse.getItems()).filter(BulkItemResponse::isFailed).count()
                                        : 0,
                                    bulkResponse.getTook()
                                );
                            wrappedListener.onResponse(bulkResponse);
                        } catch (Exception e) {
                            wrappedListener.onFailure(e);
                        }
                    }
                });
            } else {
                syncUpUndeployedModels(syncUpRequest);
                listener.onResponse(undeployModelNodesResponse);
            }
        }
    }

    @Override
    protected MLUndeployModelNodesResponse newResponse(
        MLUndeployModelNodesRequest nodesRequest,
        List<MLUndeployModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLUndeployModelNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    private Map<String, String[]> covertRemoveNodesMapForSyncUp(Map<String, List<String>> actualRemovedNodesMap) {
        Map<String, String[]> removedNodesMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : actualRemovedNodesMap.entrySet()) {
            removedNodesMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
            log.debug("removed node for model: {}, {}", entry.getKey(), Arrays.toString(entry.getValue().toArray(new String[0])));
        }
        return removedNodesMap;
    }

    private void syncUpUndeployedModels(MLSyncUpNodesRequest syncUpRequest) {
        client
            .execute(
                MLSyncUpAction.INSTANCE,
                syncUpRequest,
                ActionListener
                    .wrap(r -> log.debug("sync up removed nodes successfully"), e -> log.error("failed to sync up removed node", e))
            );
    }

    @Override
    protected MLUndeployModelNodeRequest newNodeRequest(MLUndeployModelNodesRequest request) {
        return new MLUndeployModelNodeRequest(request);
    }

    @Override
    protected MLUndeployModelNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLUndeployModelNodeResponse(in);
    }

    @Override
    protected MLUndeployModelNodeResponse nodeOperation(MLUndeployModelNodeRequest request) {
        return createUndeployModelNodeResponse(request.getMlUndeployModelNodesRequest());
    }

    private MLUndeployModelNodeResponse createUndeployModelNodeResponse(MLUndeployModelNodesRequest MLUndeployModelNodesRequest) {
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).increment();

        String[] modelIds = MLUndeployModelNodesRequest.getModelIds();

        Map<String, String[]> modelWorkerNodesMap = new HashMap<>();

        boolean specifiedModelIds = modelIds != null && modelIds.length > 0;
        String[] removedModelIds = specifiedModelIds ? modelIds : mlModelManager.getAllModelIds();
        if (removedModelIds != null) {
            for (String modelId : removedModelIds) {
                FunctionName functionName = mlModelManager.getModelFunctionName(modelId);
                String[] workerNodes = mlModelManager.getWorkerNodes(modelId, functionName);
                modelWorkerNodesMap.put(modelId, workerNodes);
            }
        }

        Map<String, String> modelUndeployStatus = mlModelManager.undeployModel(modelIds);
        mlStats.getStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT).decrement();
        return new MLUndeployModelNodeResponse(clusterService.localNode(), modelUndeployStatus, modelWorkerNodesMap);
    }
}
