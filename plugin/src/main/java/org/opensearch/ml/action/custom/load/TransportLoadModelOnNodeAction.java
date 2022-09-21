/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.load;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.engine.MLEngine.getLoadModelChunkPath;
import static org.opensearch.ml.engine.MLEngine.getLoadModelZipPath;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.log4j.Log4j2;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionListenerResponseHandler;
import org.opensearch.action.FailedNodeException;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.model.forward.MLForwardAction;
import org.opensearch.ml.common.transport.model.forward.MLForwardInput;
import org.opensearch.ml.common.transport.model.forward.MLForwardRequest;
import org.opensearch.ml.common.transport.model.forward.MLForwardRequestType;
import org.opensearch.ml.common.transport.model.forward.MLForwardResponse;
import org.opensearch.ml.common.transport.model.load.LoadModelInput;
import org.opensearch.ml.common.transport.model.load.LoadModelNodeRequest;
import org.opensearch.ml.common.transport.model.load.LoadModelNodeResponse;
import org.opensearch.ml.common.transport.model.load.LoadModelNodesRequest;
import org.opensearch.ml.common.transport.model.load.LoadModelNodesResponse;
import org.opensearch.ml.common.transport.model.load.MLLoadModelOnNodeAction;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.collect.ImmutableMap;

@Log4j2
public class TransportLoadModelOnNodeAction extends
    TransportNodesAction<LoadModelNodesRequest, LoadModelNodesResponse, LoadModelNodeRequest, LoadModelNodeResponse> {
    TransportService transportService;
    CustomModelManager customModelManager;
    MLTaskManager mlTaskManager;
    MLModelManager mlModelManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportLoadModelOnNodeAction(
        TransportService transportService,
        ActionFilters actionFilters,
        CustomModelManager customModelManager,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry
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
        this.customModelManager = customModelManager;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
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

        Map<String, String> modelLoadStatus = new HashMap<>();
        modelLoadStatus.put(modelId, "received");

        String localNodeId = clusterService.localNode().getId();
        if (!coordinatingNodeId.equals(localNodeId)) {
            mlTaskManager.add(mlTask);
        }
        loadModel(modelId, ActionListener.wrap(r -> {
            log.info("loaded model successfully " + modelId);
            if (!coordinatingNodeId.equals(localNodeId)) {
                mlTaskManager.remove(taskId);
            }
            MLForwardInput forwardInput = new MLForwardInput(
                null,
                null,
                taskId,
                modelId,
                clusterService.localNode().getId(),
                MLForwardRequestType.LOAD_MODEL_DONE,
                null,
                null,
                null
            );
            MLForwardRequest loadModelDoneMessage = new MLForwardRequest(forwardInput);
            ActionListener<MLForwardResponse> myListener = ActionListener
                .wrap(
                    res -> { log.info("Response from coordinating node is " + res); },
                    ex -> { log.error("Failed to receive response form coordinating node", ex); }
                );
            transportService
                .sendRequest(
                    getNodeById(coordinatingNodeId),
                    MLForwardAction.NAME,
                    loadModelDoneMessage,
                    new ActionListenerResponseHandler<MLForwardResponse>(myListener, MLForwardResponse::new)
                );
        }, e -> {
            log.error("Failed to load model " + modelId, e);
            mlTaskManager
                .updateMLTask(
                    taskId,
                    ImmutableMap.of(MLTask.ERROR_FIELD, ExceptionUtils.getStackTrace(e), MLTask.STATE_FIELD, MLTaskState.FAILED),
                    5000
                );
            mlTaskManager.remove(taskId);
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

    protected void loadModel(String modelId, ActionListener<String> listener) {
        try {
            threadPool.executor(TASK_THREAD_POOL).execute(() -> {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    mlModelManager.getModel(modelId, ActionListener.wrap(mlModelMeta -> {
                        retrieveModelChunks(mlModelMeta, ActionListener.wrap(modelZipFile -> {
                            customModelManager.loadModel(modelZipFile, modelId, mlModelMeta.getName(), mlModelMeta.getModelTaskType(), mlModelMeta.getVersion(), "PyTorch");
                            listener.onResponse("successful");
                        }, e -> {
                            log.error("Failed to retrieve model " + modelId, e);
                            listener.onFailure(e);
                        }));
                    }, e -> {
                        listener.onFailure(new MLResourceNotFoundException("ML model not found"));
                    }));
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to download custom model " + modelId, e);
            listener.onFailure(e);
        }
    }

    private void retrieveModelChunks(MLModel mlModelMeta, ActionListener<File> listener) throws InterruptedException {
        String modelId = mlModelMeta.getModelId();
        String modelName = mlModelMeta.getName();
        Integer totalChunks = mlModelMeta.getTotalChunks();


        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_INDEX);
        getRequest.id();

        Semaphore semaphore = new Semaphore(1);
        AtomicBoolean stopNow = new AtomicBoolean(false);
        String modelZip = getLoadModelZipPath(modelId, modelName);
        File[] chunkFiles = new File[totalChunks];
        AtomicInteger retrievedChunks = new AtomicInteger(0);
        for (int i = 0; i < totalChunks; i++) {
            if (stopNow.get()) {
                listener.onFailure(new MLException("Failed to load model"));
                return;
            }
            semaphore.tryAcquire(10, TimeUnit.SECONDS);

            String modelChunkId = mlModelManager.getModelChunkId(modelId, i);
            int currentChunk = i;
            mlModelManager.getModel(modelChunkId, ActionListener.wrap(model -> {
                Path chunkPath = getLoadModelChunkPath(modelId, currentChunk);
                customModelManager.write(Base64.getDecoder().decode(model.getContent()), chunkPath.toString());
                chunkFiles[currentChunk] = new File(chunkPath.toUri());
                semaphore.release();
                retrievedChunks.getAndIncrement();
                if (retrievedChunks.get() == totalChunks) {
                    File modelZipFile = new File(modelZip);
                    customModelManager.mergeFiles(chunkFiles, modelZipFile);
                    listener.onResponse(modelZipFile);
                }
            }, e -> {
                stopNow.set(true);
                semaphore.release();
                listener.onFailure(new MLResourceNotFoundException("Fail to find model chunk"));
                return;
            }));
        }
    }
}
