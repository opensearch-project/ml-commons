/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.load;

import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.breaker.MLCircuitBreakerService;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.transport.forward.MLForwardAction;
import org.opensearch.ml.common.transport.forward.MLForwardInput;
import org.opensearch.ml.common.transport.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.load.LoadModelInput;
import org.opensearch.ml.common.transport.load.LoadModelNodeRequest;
import org.opensearch.ml.common.transport.load.LoadModelNodeResponse;
import org.opensearch.ml.common.transport.load.LoadModelNodesRequest;
import org.opensearch.ml.common.transport.load.LoadModelNodesResponse;
import org.opensearch.ml.common.transport.load.MLLoadModelOnNodeAction;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.ml.utils.MLExceptionUtils;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportLoadModelOnNodeAction extends
    TransportNodesAction<LoadModelNodesRequest, LoadModelNodesResponse, LoadModelNodeRequest, LoadModelNodeResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    MLModelManager mlModelManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;
    MLCircuitBreakerService mlCircuitBreakerService;
    MLStats mlStats;

    @Inject
    public TransportLoadModelOnNodeAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLCircuitBreakerService mlCircuitBreakerService,
        MLStats mlStats,
        Settings settings
    ) {
        super(
            MLLoadModelOnNodeAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            LoadModelNodesRequest::new,
            LoadModelNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            LoadModelNodeResponse.class
        );
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.mlCircuitBreakerService = mlCircuitBreakerService;
        this.mlStats = mlStats;
    }

    @Override
    protected LoadModelNodesResponse newResponse(
        LoadModelNodesRequest nodesRequest,
        List<LoadModelNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new LoadModelNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected LoadModelNodeRequest newNodeRequest(LoadModelNodesRequest request) {
        return new LoadModelNodeRequest(request);
    }

    @Override
    protected LoadModelNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new LoadModelNodeResponse(in);
    }

    @Override
    protected LoadModelNodeResponse nodeOperation(LoadModelNodeRequest request) {
        return createLoadModelNodeResponse(request.getLoadModelNodesRequest());
    }

    private LoadModelNodeResponse createLoadModelNodeResponse(LoadModelNodesRequest loadModelNodesRequest) {
        LoadModelInput loadModelInput = loadModelNodesRequest.getLoadModelInput();
        String modelId = loadModelInput.getModelId();
        String taskId = loadModelInput.getTaskId();
        Integer nodeCount = loadModelInput.getNodeCount();
        String coordinatingNodeId = loadModelInput.getCoordinatingNodeId();
        MLTask mlTask = loadModelInput.getMlTask();
        String modelContentHash = loadModelInput.getModelContentHash();

        Map<String, String> modelLoadStatus = new HashMap<>();
        modelLoadStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();

        ActionListener<MLForwardResponse> taskDoneListener = ActionListener
            .wrap(
                res -> { log.info("load model task done " + taskId); },
                ex -> { logException("Load model task failed: " + taskId, ex, log); }
            );

        loadModel(modelId, modelContentHash, mlTask.getFunctionName(), localNodeId, coordinatingNodeId, mlTask, ActionListener.wrap(r -> {
            MLForwardInput mlForwardInput = MLForwardInput
                .builder()
                .requestType(MLForwardRequestType.LOAD_MODEL_DONE)
                .taskId(taskId)
                .modelId(modelId)
                .workerNodeId(clusterService.localNode().getId())
                .build();
            MLForwardRequest loadModelDoneMessage = new MLForwardRequest(mlForwardInput);

            transportService
                .sendRequest(
                    getNodeById(coordinatingNodeId),
                    MLForwardAction.NAME,
                    loadModelDoneMessage,
                    new ActionListenerResponseHandler<MLForwardResponse>(taskDoneListener, MLForwardResponse::new)
                );
        }, e -> {
            MLForwardInput mlForwardInput = MLForwardInput
                .builder()
                .requestType(MLForwardRequestType.LOAD_MODEL_DONE)
                .taskId(taskId)
                .modelId(modelId)
                .workerNodeId(clusterService.localNode().getId())
                .error(MLExceptionUtils.getRootCauseMessage(e))
                .build();
            MLForwardRequest loadModelDoneMessage = new MLForwardRequest(mlForwardInput);

            transportService
                .sendRequest(
                    getNodeById(coordinatingNodeId),
                    MLForwardAction.NAME,
                    loadModelDoneMessage,
                    new ActionListenerResponseHandler<MLForwardResponse>(taskDoneListener, MLForwardResponse::new)
                );
        }));

        return new LoadModelNodeResponse(clusterService.localNode(), modelLoadStatus);
    }

    private DiscoveryNode getNodeById(String nodeId) {
        DiscoveryNodes nodes = clusterService.state().getNodes();
        Iterator<DiscoveryNode> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            DiscoveryNode node = iterator.next();
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    private void loadModel(
        String modelId,
        String modelContentHash,
        FunctionName functionName,
        String localNodeId,
        String coordinatingNodeId,
        MLTask mlTask,
        ActionListener<String> listener
    ) {
        try {
            log.debug("start loading model {}", modelId);
            mlModelManager.loadModel(modelId, modelContentHash, functionName, mlTask, ActionListener.runBefore(listener, () -> {
                if (!coordinatingNodeId.equals(localNodeId)) {
                    mlTaskManager.remove(mlTask.getTaskId());
                }
            }));
        } catch (Exception e) {
            logException("Failed to load model " + modelId, e, log);
            listener.onFailure(e);
        }
    }

}
