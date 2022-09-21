/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.unload;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.common.transport.model.unload.MLUnloadModelAction;
import org.opensearch.ml.common.transport.model.unload.UnloadModelInput;
import org.opensearch.ml.common.transport.model.unload.UnloadModelNodeRequest;
import org.opensearch.ml.common.transport.model.unload.UnloadModelNodeResponse;
import org.opensearch.ml.common.transport.model.unload.UnloadModelNodesRequest;
import org.opensearch.ml.common.transport.model.unload.UnloadModelNodesResponse;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportUnloadModelAction extends
    TransportNodesAction<UnloadModelNodesRequest, UnloadModelNodesResponse, UnloadModelNodeRequest, UnloadModelNodeResponse> {
    private final CustomModelManager customModelManager;
    private final ClusterService clusterService;

    @Inject
    public TransportUnloadModelAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        ClusterService clusterService,
        ThreadPool threadPool
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
        this.customModelManager = customModelManager;
        this.clusterService = clusterService;
    }

    @Override
    protected UnloadModelNodesResponse newResponse(
        UnloadModelNodesRequest nodesRequest,
        List<UnloadModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
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
        UnloadModelInput unloadModelInput = unloadModelNodesRequest.getUnloadModelInput();

        Map<String, String> status = customModelManager.unloadModel(unloadModelInput);
        Map<String, String> modelUnloadStatus = new HashMap<>();
        modelUnloadStatus.putAll(status);
        for (String key : modelUnloadStatus.keySet()) {
            log.info("model unload status: key: {}, value: {}", key, modelUnloadStatus.get(key));
        }
        return new UnloadModelNodeResponse(clusterService.localNode(), modelUnloadStatus);
    }
}
