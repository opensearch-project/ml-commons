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
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeployModelControllerTransportAction extends
    TransportNodesAction<MLDeployModelControllerNodesRequest, MLDeployModelControllerNodesResponse, MLDeployModelControllerNodeRequest, MLDeployModelControllerNodeResponse> {

    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public DeployModelControllerTransportAction(
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
            MLDeployModelControllerAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLDeployModelControllerNodesRequest::new,
            MLDeployModelControllerNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLDeployModelControllerNodeResponse.class
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
    protected MLDeployModelControllerNodesResponse newResponse(
        MLDeployModelControllerNodesRequest request,
        List<MLDeployModelControllerNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLDeployModelControllerNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLDeployModelControllerNodeRequest newNodeRequest(MLDeployModelControllerNodesRequest request) {
        return new MLDeployModelControllerNodeRequest(request);
    }

    @Override
    protected MLDeployModelControllerNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLDeployModelControllerNodeResponse(in);
    }

    @Override
    protected MLDeployModelControllerNodeResponse nodeOperation(MLDeployModelControllerNodeRequest request) {
        return createDeployModelControllerNodeResponse(request.getDeployModelControllerNodesRequest());
    }

    private MLDeployModelControllerNodeResponse createDeployModelControllerNodeResponse(
        MLDeployModelControllerNodesRequest deployModelControllerNodesRequest
    ) {
        String modelId = deployModelControllerNodesRequest.getModelId();

        Map<String, String> modelControllerDeployStatus = new HashMap<>();
        modelControllerDeployStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.deployModelControllerWithDeployedModel(modelId, ActionListener.wrap(r -> {
            modelControllerDeployStatus.replace(modelId, "success");
            log.info("Successfully deployed model controller for model {} on node {}", modelId, localNodeId);
        }, e -> {
            modelControllerDeployStatus.replace(modelId, "failed");
            log.error("Failed to deploy model controller for model {} on node {}", modelId, localNodeId, e);
        }));
        return new MLDeployModelControllerNodeResponse(clusterService.localNode(), modelControllerDeployStatus);
    }
}
