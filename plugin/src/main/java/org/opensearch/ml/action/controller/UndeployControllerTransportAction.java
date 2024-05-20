/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.controller;

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
import org.opensearch.ml.common.transport.controller.MLUndeployControllerAction;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UndeployControllerTransportAction extends
    TransportNodesAction<MLUndeployControllerNodesRequest, MLUndeployControllerNodesResponse, MLUndeployControllerNodeRequest, MLUndeployControllerNodeResponse> {

    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public UndeployControllerTransportAction(
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
            MLUndeployControllerAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLUndeployControllerNodesRequest::new,
            MLUndeployControllerNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLUndeployControllerNodeResponse.class
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
    protected MLUndeployControllerNodesResponse newResponse(
        MLUndeployControllerNodesRequest request,
        List<MLUndeployControllerNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLUndeployControllerNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLUndeployControllerNodeRequest newNodeRequest(MLUndeployControllerNodesRequest request) {
        return new MLUndeployControllerNodeRequest(request);
    }

    @Override
    protected MLUndeployControllerNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLUndeployControllerNodeResponse(in);
    }

    @Override
    protected MLUndeployControllerNodeResponse nodeOperation(MLUndeployControllerNodeRequest request) {
        return createUndeployControllerNodeResponse(request.getUndeployControllerNodesRequest());
    }

    private MLUndeployControllerNodeResponse createUndeployControllerNodeResponse(
        MLUndeployControllerNodesRequest undeployControllerNodesRequest
    ) {
        String modelId = undeployControllerNodesRequest.getModelId();

        Map<String, String> controllerUndeployStatus = new HashMap<>();
        controllerUndeployStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.undeployController(modelId, ActionListener.wrap(r -> {
            log.info("Successfully undeployed model controller for the given model on node {}", localNodeId);
        }, e -> { log.error("Failed to undeploy model controller for the given model on node {}", localNodeId, e); }));
        return new MLUndeployControllerNodeResponse(clusterService.localNode(), controllerUndeployStatus);
    }
}
