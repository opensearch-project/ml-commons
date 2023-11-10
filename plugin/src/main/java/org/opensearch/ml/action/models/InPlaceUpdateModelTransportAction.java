/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

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
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelAction;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodeRequest;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodeResponse;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodesRequest;
import org.opensearch.ml.common.transport.model.MLInPlaceUpdateModelNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class InPlaceUpdateModelTransportAction extends
    TransportNodesAction<MLInPlaceUpdateModelNodesRequest, MLInPlaceUpdateModelNodesResponse, MLInPlaceUpdateModelNodeRequest, MLInPlaceUpdateModelNodeResponse> {
    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public InPlaceUpdateModelTransportAction(
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
            MLInPlaceUpdateModelAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLInPlaceUpdateModelNodesRequest::new,
            MLInPlaceUpdateModelNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLInPlaceUpdateModelNodeResponse.class
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
    protected MLInPlaceUpdateModelNodesResponse newResponse(
        MLInPlaceUpdateModelNodesRequest nodesRequest,
        List<MLInPlaceUpdateModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLInPlaceUpdateModelNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLInPlaceUpdateModelNodeRequest newNodeRequest(MLInPlaceUpdateModelNodesRequest request) {
        return new MLInPlaceUpdateModelNodeRequest(request);
    }

    @Override
    protected MLInPlaceUpdateModelNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLInPlaceUpdateModelNodeResponse(in);
    }

    @Override
    protected MLInPlaceUpdateModelNodeResponse nodeOperation(MLInPlaceUpdateModelNodeRequest request) {
        return createInPlaceUpdateModelNodeResponse(request.getMlInPlaceUpdateModelNodesRequest());
    }

    private MLInPlaceUpdateModelNodeResponse createInPlaceUpdateModelNodeResponse(
        MLInPlaceUpdateModelNodesRequest mlInPlaceUpdateModelNodesRequest
    ) {
        String modelId = mlInPlaceUpdateModelNodesRequest.getModelId();
        boolean updatePredictorFlag = mlInPlaceUpdateModelNodesRequest.isUpdatePredictorFlag();

        Map<String, String> modelUpdateStatus = new HashMap<>();
        modelUpdateStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.inplaceUpdateModel(modelId, updatePredictorFlag, ActionListener.wrap(r -> {
            log.info("Successfully performing in-place update model {} on node {}", modelId, localNodeId);
        }, e -> { log.error("Failed to perform in-place update model for model {} on node {}", modelId, localNodeId); }));
        return new MLInPlaceUpdateModelNodeResponse(clusterService.localNode(), modelUpdateStatus);
    }
}
