/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.undeploy;

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
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
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
        return new MLUndeployModelNodesResponse(clusterService.getClusterName(), responses, failures);
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
