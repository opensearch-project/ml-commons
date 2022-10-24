/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.syncup;

import static org.opensearch.ml.engine.MLEngine.getLoadModelPath;
import static org.opensearch.ml.engine.MLEngine.getModelCachePath;
import static org.opensearch.ml.engine.MLEngine.getUploadModelPath;
import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.utils.FileUtils;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

@Log4j2
public class TransportSyncUpOnNodeAction extends
    TransportNodesAction<MLSyncUpNodesRequest, MLSyncUpNodesResponse, MLSyncUpNodeRequest, MLSyncUpNodeResponse> {
    TransportService transportService;
    ModelHelper modelHelper;
    MLTaskManager mlTaskManager;
    MLModelManager mlModelManager;
    ClusterService clusterService;
    ThreadPool threadPool;
    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportSyncUpOnNodeAction(
        TransportService transportService,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(
            MLSyncUpAction.NAME,
            threadPool,
            clusterService,
            transportService,
            actionFilters,
            MLSyncUpNodesRequest::new,
            MLSyncUpNodeRequest::new,
            ThreadPool.Names.MANAGEMENT,
            MLSyncUpNodeResponse.class
        );
        this.transportService = transportService;
        this.modelHelper = modelHelper;
        this.mlTaskManager = mlTaskManager;
        this.mlModelManager = mlModelManager;
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected MLSyncUpNodesResponse newResponse(
        MLSyncUpNodesRequest nodesRequest,
        List<MLSyncUpNodeResponse> responses,
        List<FailedNodeException> failures
    ) {
        return new MLSyncUpNodesResponse(clusterService.getClusterName(), responses, failures);
    }

    @Override
    protected MLSyncUpNodeRequest newNodeRequest(MLSyncUpNodesRequest request) {
        return new MLSyncUpNodeRequest(request);
    }

    @Override
    protected MLSyncUpNodeResponse newNodeResponse(StreamInput in) throws IOException {
        return new MLSyncUpNodeResponse(in);
    }

    @Override
    protected MLSyncUpNodeResponse nodeOperation(MLSyncUpNodeRequest request) {
        return createSyncUpNodeResponse(request.getSyncUpNodesRequest());
    }

    private MLSyncUpNodeResponse createSyncUpNodeResponse(MLSyncUpNodesRequest loadModelNodesRequest) {
        MLSyncUpInput syncUpInput = loadModelNodesRequest.getSyncUpInput();
        Map<String, String[]> addedWorkerNodes = syncUpInput.getAddedWorkerNodes();
        Map<String, String[]> removedWorkerNodes = syncUpInput.getRemovedWorkerNodes();
        Map<String, Set<String>> modelRoutingTable = syncUpInput.getModelRoutingTable();
        Map<String, Set<String>> runningLoadModelTasks = syncUpInput.getRunningLoadModelTasks();

        if (addedWorkerNodes != null && addedWorkerNodes.size() > 0) {
            for (Map.Entry<String, String[]> entry : addedWorkerNodes.entrySet()) {
                mlModelManager.addModelWorkerNode(entry.getKey(), entry.getValue());
            }
        }
        if (removedWorkerNodes != null && removedWorkerNodes.size() > 0) {
            for (Map.Entry<String, String[]> entry : removedWorkerNodes.entrySet()) {
                mlModelManager.removeModelWorkerNode(entry.getKey(), entry.getValue());
            }
        }

        String[] loadedModelIds = null;
        String[] runningLoadModelTaskIds = null;
        if (syncUpInput.isGetLoadedModels()) {
            loadedModelIds = mlModelManager.getLocalLoadedModels();
            runningLoadModelTaskIds = mlTaskManager.getLocalRunningLoadModelTasks();
        }

        if (syncUpInput.isClearRoutingTable()) {
            mlModelManager.clearRoutingTable();
        } else if (modelRoutingTable != null) {
            for (Map.Entry<String, Set<String>> entry : modelRoutingTable.entrySet()) {
                log.debug("latest routing table for model: {}:  {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            mlModelManager.syncModelWorkerNodes(modelRoutingTable);
        }

        if (syncUpInput.isSyncRunningLoadModelTasks()) {
            mlTaskManager.syncRunningLoadModelTasks(runningLoadModelTasks);
        }

        cleanUpLocalCacheFiles();

        return new MLSyncUpNodeResponse(clusterService.localNode(), "ok", loadedModelIds, runningLoadModelTaskIds);
    }

    private void cleanUpLocalCacheFiles() {
        Path uploadModelRootPath = MLEngine.getUploadModelRootPath();
        Path loadModelRootPath = MLEngine.getLoadModelRootPath();
        Path modelCacheRootPath = MLEngine.getModelCacheRootPath();
        Set<String> modelsInCacheFolder = FileUtils.getFileNames(uploadModelRootPath, loadModelRootPath, modelCacheRootPath);
        if (modelsInCacheFolder.size() > 0) {
            log
                .debug(
                    "Found {} models in cache folder: {}",
                    modelsInCacheFolder.size(),
                    Arrays.toString(modelsInCacheFolder.toArray(new String[0]))
                );
            for (String modelId : modelsInCacheFolder) {
                if (!mlTaskManager.contains(modelId)
                    && !mlTaskManager.containsModel(modelId)
                    && !mlModelManager.isModelRunningOnNode(modelId)) {
                    log.info("ML model not in cache. Remove all of its cache files. model id: {}", modelId);
                    deleteFileCache(modelId);
                }
            }
        }
    }

    private void deleteFileCache(String modelId) {
        deleteFileQuietly(getModelCachePath(modelId));
        deleteFileQuietly(getLoadModelPath(modelId));
        deleteFileQuietly(getUploadModelPath(modelId));
    }
}
