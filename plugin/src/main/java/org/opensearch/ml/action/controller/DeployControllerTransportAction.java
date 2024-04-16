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
import org.opensearch.ml.common.transport.controller.MLDeployControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLDeployControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeployControllerTransportAction extends
    TransportNodesAction<MLDeployControllerNodesRequest, MLDeployControllerNodesResponse, MLDeployControllerNodeRequest, MLDeployControllerNodeResponse> {

    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeployControllerTransportAction(
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
            MLDeployControllerAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLDeployControllerNodesRequest::new,
            MLDeployControllerNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLDeployControllerNodeResponse.class
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
    protected MLDeployControllerNodesResponse newResponse(
        MLDeployControllerNodesRequest request,
        List<MLDeployControllerNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLDeployControllerNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLDeployControllerNodeRequest newNodeRequest(MLDeployControllerNodesRequest request) {
        return new MLDeployControllerNodeRequest(request);
    }

    @Override
    protected MLDeployControllerNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLDeployControllerNodeResponse(in);
    }

    @Override
    protected MLDeployControllerNodeResponse nodeOperation(MLDeployControllerNodeRequest request) {
        return createDeployControllerNodeResponse(request.getDeployControllerNodesRequest());
    }

    private MLDeployControllerNodeResponse createDeployControllerNodeResponse(MLDeployControllerNodesRequest deployControllerNodesRequest) {
        String modelId = deployControllerNodesRequest.getModelId();

        Map<String, String> controllerDeployStatus = new HashMap<>();
        controllerDeployStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.deployControllerWithDeployedModel(modelId, ActionListener.wrap(r -> {
            log.info("Successfully deployed model controller on node {}", localNodeId);
        }, e -> { log.error("Failed to deploy model controller on node {}", localNodeId, e); }));
        return new MLDeployControllerNodeResponse(clusterService.localNode(), controllerDeployStatus);
    }
}
