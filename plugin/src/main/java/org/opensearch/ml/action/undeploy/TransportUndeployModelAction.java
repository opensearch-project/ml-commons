/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.NOT_FOUND;
import static org.opensearch.ml.common.CommonValue.UNDEPLOYED;
import static org.opensearch.ml.common.MLModel.MODEL_STATE_FIELD;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
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
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@Log4j2
public class TransportUndeployModelAction extends
    TransportNodesAction<MLUndeployModelNodesRequest, MLUndeployModelNodesResponse, MLUndeployModelNodeRequest, MLUndeployModelNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;

    @Inject
    public TransportUndeployModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
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
        this.nodeFilter = nodeFilter;
        this.mlStats = mlStats;
    }

    @Override
    protected MLUndeployModelNodesResponse newResponse(
        MLUndeployModelNodesRequest nodesRequest,
        List<MLUndeployModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        if (responses != null) {
            Map<String, List<String>> removedNodeMap = new HashMap<>();
            Map<String, Integer> modelWorkNodeCounts = new HashMap<>();
            responses.stream().forEach(r -> {
                Set<String> notFoundModels = new HashSet<>();
                Map<String, Integer> nodeCounts = r.getModelWorkerNodeCounts();
                if (nodeCounts != null) {
                    for (Map.Entry<String, Integer> entry : nodeCounts.entrySet()) {
                        if (!modelWorkNodeCounts.containsKey(entry.getKey())
                            || modelWorkNodeCounts.get(entry.getKey()) < entry.getValue()) {
                            modelWorkNodeCounts.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                Map<String, String> modelUndeployStatus = r.getModelUndeployStatus();
                for (Map.Entry<String, String> entry : modelUndeployStatus.entrySet()) {
                    String status = entry.getValue();
                    if (UNDEPLOYED.equals(status) || NOT_FOUND.equals(status)) {
                        String modelId = entry.getKey();
                        if (!removedNodeMap.containsKey(modelId)) {
                            removedNodeMap.put(modelId, new ArrayList<>());
                        }
                        removedNodeMap.get(modelId).add(r.getNode().getId());
                    }
                    if (NOT_FOUND.equals(status)) {
                        notFoundModels.add(entry.getKey());
                    }
                }
                notFoundModels.forEach(m -> modelUndeployStatus.remove(m));
            });
            Map<String, String[]> removedNodes = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : removedNodeMap.entrySet()) {
                removedNodes.put(entry.getKey(), entry.getValue().toArray(new String[0]));
                log.debug("removed node for model: {}, {}", entry.getKey(), Arrays.toString(entry.getValue().toArray(new String[0])));
            }
            MLSyncUpInput syncUpInput = MLSyncUpInput.builder().removedWorkerNodes(removedNodes).build();

            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(nodeFilter.getAllNodes(), syncUpInput);
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                if (removedNodeMap.size() > 0) {
                    BulkRequest bulkRequest = new BulkRequest();
                    for (String modelId : removedNodeMap.keySet()) {
                        UpdateRequest updateRequest = new UpdateRequest();
                        int removedNodeCount = removedNodeMap.get(modelId).size();
                        MLModelState mlModelState = modelWorkNodeCounts.get(modelId) > removedNodeCount
                            ? MLModelState.PARTIALLY_DEPLOYED
                            : MLModelState.UNDEPLOYED;
                        updateRequest.index(ML_MODEL_INDEX).id(modelId).doc(ImmutableMap.of(MODEL_STATE_FIELD, mlModelState));
                        bulkRequest.add(updateRequest);
                    }
                    ActionListener<BulkResponse> actionListenr = ActionListener
                        .wrap(
                            r -> {
                                log
                                    .debug(
                                        "updated model state as undeployed for : {}",
                                        Arrays.toString(removedNodeMap.keySet().toArray(new String[0]))
                                    );
                            },
                            e -> { log.error("Failed to update model state as undeployed", e); }
                        );
                    client.bulk(bulkRequest, ActionListener.runAfter(actionListenr, () -> { syncUpUndeployedModels(syncUpRequest); }));
                } else {
                    syncUpUndeployedModels(syncUpRequest);
                }
            }
        }
        return new MLUndeployModelNodesResponse(clusterService.getClusterName(), responses, failures);
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
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();

        String[] modelIds = MLUndeployModelNodesRequest.getModelIds();

        Map<String, Integer> modelWorkerNodeCounts = new HashMap<>();
        boolean specifiedModelIds = modelIds != null && modelIds.length > 0;
        String[] removedModelIds = specifiedModelIds ? modelIds : mlModelManager.getAllModelIds();
        if (removedModelIds != null) {
            for (String modelId : removedModelIds) {
                String[] workerNodes = mlModelManager.getWorkerNodes(modelId);
                modelWorkerNodeCounts.put(modelId, workerNodes == null ? 0 : workerNodes.length);
            }
        }

        Map<String, String> modelUndeployStatus = mlModelManager.undeployModel(modelIds);
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).decrement();
        return new MLUndeployModelNodeResponse(clusterService.localNode(), modelUndeployStatus, modelWorkerNodeCounts);
    }
}
