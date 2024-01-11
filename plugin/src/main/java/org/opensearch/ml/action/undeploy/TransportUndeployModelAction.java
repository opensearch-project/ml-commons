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

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
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
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportUndeployModelAction extends
    TransportNodesAction<MLUndeployModelNodesRequest, MLUndeployModelNodesResponse, MLUndeployModelNodeRequest, MLUndeployModelNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public TransportUndeployModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        DiscoveryNodeHelper nodeFilter,
        MLStats mlStats,
        NamedXContentRegistry xContentRegistry,
        ModelAccessControlHelper modelAccessControlHelper
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
        this.nodeFilter = nodeFilter;
        this.mlStats = mlStats;
        this.xContentRegistry = xContentRegistry;
        this.modelAccessControlHelper = modelAccessControlHelper;
    }

    @Override
    protected MLUndeployModelNodesResponse newResponse(
        MLUndeployModelNodesRequest nodesRequest,
        List<MLUndeployModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        if (responses != null) {
            Map<String, List<String>> actualRemovedNodesMap = new HashMap<>();
            Map<String, String[]> modelWorkNodesBeforeRemoval = new HashMap<>();
            responses.forEach(r -> {
                Map<String, String[]> nodeCounts = r.getModelWorkerNodeBeforeRemoval();

                if (nodeCounts != null) {
                    for (Map.Entry<String, String[]> entry : nodeCounts.entrySet()) {
                        // when undeploy a undeployed model, the entry.getvalue() is null
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
                if (actualRemovedNodesMap.size() > 0) {
                    BulkRequest bulkRequest = new BulkRequest();
                    Map<String, Boolean> deployToAllNodes = new HashMap<>();
                    for (String modelId : actualRemovedNodesMap.keySet()) {
                        UpdateRequest updateRequest = new UpdateRequest();
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
                            updateDocument.put(MLModel.PLANNING_WORKER_NODES_FIELD, List.of());
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
                        updateRequest.index(ML_MODEL_INDEX).id(modelId).doc(updateDocument);
                        bulkRequest.add(updateRequest);
                    }
                    syncUpInput.setDeployToAllNodes(deployToAllNodes);
                    ActionListener<BulkResponse> actionListener = ActionListener.wrap(r -> {
                        log
                            .debug(
                                "updated model state as undeployed for : {}",
                                Arrays.toString(actualRemovedNodesMap.keySet().toArray(new String[0]))
                            );
                    }, e -> { log.error("Failed to update model state as undeployed", e); });
                    client.bulk(bulkRequest, ActionListener.runAfter(actionListener, () -> { syncUpUndeployedModels(syncUpRequest); }));
                } else {
                    syncUpUndeployedModels(syncUpRequest);
                }
            }
        }
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
