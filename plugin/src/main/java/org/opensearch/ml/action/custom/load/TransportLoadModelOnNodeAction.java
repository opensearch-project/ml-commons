/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.custom.load;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.engine.MLEngine.getLoadModelChunkPath;
import static org.opensearch.ml.engine.MLEngine.getLoadModelZipPath;
import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
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
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.custom.MLForwardAction;
import org.opensearch.ml.common.transport.custom.MLForwardInput;
import org.opensearch.ml.common.transport.custom.MLForwardRequest;
import org.opensearch.ml.common.transport.custom.MLForwardRequestType;
import org.opensearch.ml.common.transport.custom.MLForwardResponse;
import org.opensearch.ml.common.transport.custom.load.LoadModelInput;
import org.opensearch.ml.common.transport.custom.load.LoadModelNodeRequest;
import org.opensearch.ml.common.transport.custom.load.LoadModelNodeResponse;
import org.opensearch.ml.common.transport.custom.load.LoadModelNodesRequest;
import org.opensearch.ml.common.transport.custom.load.LoadModelNodesResponse;
import org.opensearch.ml.common.transport.custom.load.MLLoadModelOnNodeAction;
import org.opensearch.ml.engine.algorithms.custom.CustomModelManager;
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
        String modelName = loadModelInput.getModelName();
        Integer version = loadModelInput.getVersion();
        String taskId = loadModelInput.getTaskId();
        Integer nodeCount = loadModelInput.getNodeCount();
        String coordinatingNodeId = loadModelInput.getCoordinatingNodeId();
        MLTask mlTask = loadModelInput.getMlTask();

        Map<String, String> modelLoadStatus = new HashMap<>();
        String key = CustomModelManager.cacheKey(modelName, version);
        modelLoadStatus.put(key, "received");

        String localNodeId = clusterService.localNode().getId();
        if (!coordinatingNodeId.equals(localNodeId)) {
            mlTaskManager.add(mlTask);
        }
        loadModel(modelName, version, ActionListener.wrap(r -> {
            log.info("loaded model successfully " + modelName);
            if (!coordinatingNodeId.equals(localNodeId)) {
                mlTaskManager.remove(taskId);
            }
            MLForwardInput forwardInput = new MLForwardInput(
                modelName,
                version,
                taskId,
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
            log.error("Failed to load model " + modelName, e);
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

    protected void loadModel(String modelName, Integer version, ActionListener<String> listener) {
        try {
            threadPool.executor(TASK_THREAD_POOL).execute(() -> {
                retrieveModel(modelName, version, ActionListener.wrap(modelZipPath -> {
                    customModelManager.loadModel(modelZipPath, modelName, version, "PyTorch");
                    listener.onResponse("successful");
                }, e -> {
                    log.error("Failed to retrieve model " + modelName, e);
                    listener.onFailure(e);
                }));
            });
        } catch (Exception e) {
            log.error("Failed to download custom model " + modelName, e);
            listener.onFailure(e);
        }
    }

    private void retrieveModel(String modelName, Integer version, ActionListener<String> listener) {
        GetRequest getRequest = new GetRequest();
        getRequest.index(ML_MODEL_INDEX);
        getRequest.id(MLModel.customModelId(modelName, version, 0));

        client.get(getRequest, ActionListener.wrap(r -> {
            if (r != null && r.isExists()) {
                try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                    ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                    MLModel mlModel = MLModel.parse(parser);

                    Integer chunkNumber = mlModel.getChunkNumber();
                    Integer totalChunks = mlModel.getTotalChunks();
                    Path modelChunkPath = getLoadModelChunkPath(modelName, version, chunkNumber);
                    customModelManager.write(Base64.getDecoder().decode(mlModel.getContent()), modelChunkPath.toString());
                    mlModel = null;

                    File[] chunkFiles = new File[totalChunks];
                    chunkFiles[0] = new File(modelChunkPath.toUri());
                    AtomicInteger retricedChunks = new AtomicInteger(0);

                    Semaphore semaphore = new Semaphore(1);
                    AtomicBoolean stopNow = new AtomicBoolean(false);
                    String modelZip = getLoadModelZipPath(modelName, version);
                    for (int i = 1; i < totalChunks; i++) {
                        if (stopNow.get()) {
                            listener.onFailure(new MLException("Failed to load model"));
                            return;
                        }
                        semaphore.tryAcquire();
                        GetRequest request = new GetRequest(ML_MODEL_INDEX).id(MLModel.customModelId(modelName, version, i));

                        int currentChunk = i;
                        client.get(request, ActionListener.wrap(res -> {
                            if (res != null && res.isExists()) {
                                try (
                                    XContentParser contentParser = createXContentParserFromRegistry(
                                        xContentRegistry,
                                        res.getSourceAsBytesRef()
                                    )
                                ) {
                                    ensureExpectedToken(XContentParser.Token.START_OBJECT, contentParser.nextToken(), contentParser);
                                    MLModel model = MLModel.parse(contentParser);

                                    Path chunkPath = getLoadModelChunkPath(modelName, version, currentChunk);
                                    customModelManager.write(Base64.getDecoder().decode(model.getContent()), chunkPath.toString());
                                    chunkFiles[currentChunk] = new File(chunkPath.toUri());
                                    model = null;
                                    semaphore.release();
                                    retricedChunks.getAndIncrement();
                                    if (retricedChunks.get() == totalChunks - 1) {
                                        customModelManager.mergeFiles(chunkFiles, new File(modelZip));
                                        listener.onResponse(modelZip);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to parse ml model " + res.getId(), e);
                                    listener.onFailure(e);
                                    semaphore.release();
                                    stopNow.set(true);
                                }
                            } else {
                                stopNow.set(true);
                                semaphore.release();
                                listener.onFailure(new MLResourceNotFoundException("Fail to find task"));
                                return;
                            }
                        }, ex -> {
                            stopNow.set(true);
                            semaphore.release();
                            listener.onFailure(ex);
                            return;
                        }));
                    }
                } catch (Exception e) {
                    log.error("Failed to parse ml task" + r.getId(), e);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new MLResourceNotFoundException("Fail to find model"));
            }
        }, e -> { listener.onFailure(e); }));
    }
}
