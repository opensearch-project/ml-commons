/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.unload;

import static org.opensearch.ml.common.CommonValue.NOT_FOUND;
import static org.opensearch.ml.common.CommonValue.UNLOADED;

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
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.unload.UnloadModelNodeRequest;
import org.opensearch.ml.common.transport.unload.UnloadModelNodeResponse;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesRequest;
import org.opensearch.ml.common.transport.unload.UnloadModelNodesResponse;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUnloadModelAction extends
    TransportNodesAction<UnloadModelNodesRequest, UnloadModelNodesResponse, UnloadModelNodeRequest, UnloadModelNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;

    @Inject
    public TransportUnloadModelAction(
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
            MLUnloadModelAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            UnloadModelNodesRequest::new,
            UnloadModelNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            UnloadModelNodeResponse.class
        );
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.client = client;
        this.nodeFilter = nodeFilter;
        this.mlStats = mlStats;
    }

    @Override
    protected UnloadModelNodesResponse newResponse(
        UnloadModelNodesRequest nodesRequest,
        List<UnloadModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        if (responses != null) {
            Map<String, List<String>> removedNodeMap = new HashMap<>();
            responses.stream().forEach(r -> {
                Set<String> notFoundModels = new HashSet<>();
                Map<String, String> modelUnloadStatus = r.getModelUnloadStatus();
                for (Map.Entry<String, String> entry : modelUnloadStatus.entrySet()) {
                    String status = entry.getValue();
                    if (UNLOADED.equals(status) || NOT_FOUND.equals(status)) {
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
                notFoundModels.forEach(m -> modelUnloadStatus.remove(m));
            });
            Map<String, String[]> removedNodes = new HashMap<>();
            for (Map.Entry<String, List<String>> entry : removedNodeMap.entrySet()) {
                removedNodes.put(entry.getKey(), entry.getValue().toArray(new String[0]));
                log.debug("removed node for model: {}, {}", entry.getKey(), Arrays.toString(entry.getValue().toArray(new String[0])));
            }
            MLSyncUpInput syncUpInput = MLSyncUpInput.builder().removedWorkerNodes(removedNodes).build();

            MLSyncUpNodesRequest syncUpRequest = new MLSyncUpNodesRequest(nodeFilter.getAllNodes(), syncUpInput);
            client
                .execute(
                    MLSyncUpAction.INSTANCE,
                    syncUpRequest,
                    ActionListener
                        .wrap(r -> { log.info("sync up removed nodes"); }, e -> { log.error("failed to sync up removed node", e); })
                );
        }
        return new UnloadModelNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected UnloadModelNodeRequest newNodeRequest(UnloadModelNodesRequest request) {
        return new UnloadModelNodeRequest(request);
    }

    @Override
    protected UnloadModelNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new UnloadModelNodeResponse(in);
    }

    @Override
    protected UnloadModelNodeResponse nodeOperation(UnloadModelNodeRequest request) {
        return createUnloadModelNodeResponse(request.getUnloadModelNodesRequest());
    }

    private UnloadModelNodeResponse createUnloadModelNodeResponse(UnloadModelNodesRequest unloadModelNodesRequest) {
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).increment();
        mlStats.getStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT).increment();

        String[] modelIds = unloadModelNodesRequest.getModelIds();
        Map<String, String> modelUnloadStatus = mlModelManager.unloadModel(modelIds);
        mlStats.getStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT).decrement();
        return new UnloadModelNodeResponse(clusterService.localNode(), modelUnloadStatus);
    }
}
