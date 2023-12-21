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
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodeRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodeResponse;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUndeployModelControllerNodesResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UndeployModelControllerTransportAction extends
    TransportNodesAction<MLUndeployModelControllerNodesRequest, MLUndeployModelControllerNodesResponse, MLUndeployModelControllerNodeRequest, MLUndeployModelControllerNodeResponse> {

    private final MLModelManager mlModelManager;
    private final ClusterService clusterService;
    private final Client client;
    private DiscoveryNodeHelper nodeFilter;
    private final MLStats mlStats;
    private NamedXContentRegistry xContentRegistry;

    private ModelAccessControlHelper modelAccessControlHelper;

    @Inject
    public UndeployModelControllerTransportAction(
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
            MLUndeployModelControllerAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLUndeployModelControllerNodesRequest::new,
            MLUndeployModelControllerNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLUndeployModelControllerNodeResponse.class
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
    protected MLUndeployModelControllerNodesResponse newResponse(
        MLUndeployModelControllerNodesRequest request,
        List<MLUndeployModelControllerNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLUndeployModelControllerNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLUndeployModelControllerNodeRequest newNodeRequest(MLUndeployModelControllerNodesRequest request) {
        return new MLUndeployModelControllerNodeRequest(request);
    }

    @Override
    protected MLUndeployModelControllerNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLUndeployModelControllerNodeResponse(in);
    }

    @Override
    protected MLUndeployModelControllerNodeResponse nodeOperation(MLUndeployModelControllerNodeRequest request) {
        return createUndeployModelControllerNodeResponse(request.getUndeployModelControllerNodesRequest());
    }

    private MLUndeployModelControllerNodeResponse createUndeployModelControllerNodeResponse(
        MLUndeployModelControllerNodesRequest undeployModelControllerNodesRequest
    ) {
        String modelId = undeployModelControllerNodesRequest.getModelId();

        Map<String, String> modelControllerUndeployStatus = new HashMap<>();
        modelControllerUndeployStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        mlModelManager.undeployModelController(modelId, ActionListener.wrap(r -> {
            modelControllerUndeployStatus.replace(modelId, "success");
            log.info("Successfully undeployed model controller for model {} on node {}", modelId, localNodeId);
        }, e -> {
            modelControllerUndeployStatus.replace(modelId, "failed");
            log.error("Failed to undeploy model controller for model {} on node {}", modelId, localNodeId, e);
        }));
        return new MLUndeployModelControllerNodeResponse(clusterService.localNode(), modelControllerUndeployStatus);
    }
}
