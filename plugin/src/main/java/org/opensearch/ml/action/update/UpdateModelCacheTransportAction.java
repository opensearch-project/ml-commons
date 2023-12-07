/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.update;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.transport.update.MLUpdateModelCacheAction;
import org.opensearch.ml.common.transport.update.MLUpdateModelCacheNodeRequest;
import org.opensearch.ml.common.transport.update.MLUpdateModelCacheNodeResponse;
import org.opensearch.ml.common.transport.update.MLUpdateModelCacheNodesRequest;
import org.opensearch.ml.common.transport.update.MLUpdateModelCacheNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateModelCacheTransportAction extends
    TransportNodesAction<MLUpdateModelCacheNodesRequest, MLUpdateModelCacheNodesResponse, MLUpdateModelCacheNodeRequest, MLUpdateModelCacheNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public UpdateModelCacheTransportAction(
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
            MLUpdateModelCacheAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLUpdateModelCacheNodesRequest::new,
            MLUpdateModelCacheNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLUpdateModelCacheNodeResponse.class
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
    protected MLUpdateModelCacheNodesResponse newResponse(
        MLUpdateModelCacheNodesRequest nodesRequest,
        List<MLUpdateModelCacheNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLUpdateModelCacheNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLUpdateModelCacheNodeRequest newNodeRequest(MLUpdateModelCacheNodesRequest request) {
        return new MLUpdateModelCacheNodeRequest(request);
    }

    @Override
    protected MLUpdateModelCacheNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLUpdateModelCacheNodeResponse(in);
    }

    @Override
    protected MLUpdateModelCacheNodeResponse nodeOperation(MLUpdateModelCacheNodeRequest request) {
        return createUpdateModelCacheNodeResponse(request.getUpdateModelCacheNodesRequest());
    }

    private MLUpdateModelCacheNodeResponse createUpdateModelCacheNodeResponse(
        MLUpdateModelCacheNodesRequest mlUpdateModelCacheNodesRequest
    ) {
        String modelId = mlUpdateModelCacheNodesRequest.getModelId();

        Map<String, String> modelUpdateStatus = new HashMap<>();
        modelUpdateStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.inplaceUpdateModel(modelId, ActionListener.wrap(r -> {
            log.info("Successfully performing in-place update model {} on node {}", modelId, localNodeId);
        }, e -> { log.error("Failed to perform in-place update model for model {} on node {}", modelId, localNodeId); }));
        return new MLUpdateModelCacheNodeResponse(clusterService.localNode(), modelUpdateStatus);
    }
}
