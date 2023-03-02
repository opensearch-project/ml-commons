/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.syncup;

import static org.opensearch.ml.engine.utils.FileUtils.deleteFileQuietly;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
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
import org.opensearch.common.settings.Settings;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.model.MLModelState;
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
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;

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
    MLEngine mlEngine;

    private volatile Integer mlTaskTimeout;

    @Inject
    public TransportSyncUpOnNodeAction(
        TransportService transportService,
        Settings settings,
        ActionFilters actionFilters,
        ModelHelper modelHelper,
        MLTaskManager mlTaskManager,
        MLModelManager mlModelManager,
        ClusterService clusterService,
        ThreadPool threadPool,
        Client client,
        NamedXContentRegistry xContentRegistry,
        MLEngine mlEngine
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
        this.mlEngine = mlEngine;

        this.mlTaskTimeout = ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS, it -> { mlTaskTimeout = it; });
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

        cleanUpLocalCache();
        cleanUpLocalCacheFiles();

        return new MLSyncUpNodeResponse(clusterService.localNode(), "ok", loadedModelIds, runningLoadModelTaskIds);
    }

    @VisibleForTesting
    void cleanUpLocalCache() {
        String[] allTaskIds = mlTaskManager.getAllTaskIds();
        if (allTaskIds == null) {
            return;
        }
        for (String taskId : allTaskIds) {
            MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(taskId);
            MLTask mlTask = mlTaskCache.getMlTask();
            Instant lastUpdateTime = mlTask.getLastUpdateTime();
            Instant now = Instant.now();
            if (now.isAfter(lastUpdateTime.plusSeconds(mlTaskTimeout))) {
                log.info("ML task timeout. task id: {}, task type: {}", taskId, mlTask.getTaskType());
                mlTaskManager
                    .updateMLTask(
                        taskId,
                        ImmutableMap
                            .of(MLTask.STATE_FIELD, MLTaskState.FAILED, MLTask.ERROR_FIELD, "timeout after " + mlTaskTimeout + " seconds"),
                        10_000,
                        true
                    );

                if (mlTask.getTaskType() == MLTaskType.LOAD_MODEL) {
                    String modelId = mlTask.getModelId();
                    String[] workerNodes = mlModelManager.getWorkerNodes(modelId);
                    MLModelState modelState;
                    if (workerNodes == null || workerNodes.length == 0) {
                        modelState = MLModelState.LOAD_FAILED;
                    } else if (mlTask.getWorkerNodes().size() > workerNodes.length) {
                        modelState = MLModelState.PARTIALLY_LOADED;
                    } else {
                        modelState = MLModelState.LOADED;
                        if (mlTask.getWorkerNodes().size() < workerNodes.length) {
                            log
                                .warn(
                                    "Model loaded on more nodes than target worker nodes. taskId:{}, modelId: {}, workerNodes: {}, targetWorkerNodes: {}",
                                    taskId,
                                    modelId,
                                    Arrays.toString(workerNodes),
                                    Arrays.toString(mlTask.getWorkerNodes().toArray(new String[0]))
                                );
                        }
                    }
                    log.info("Reset model state as {} for model {}", modelState, modelId);
                    mlModelManager.updateModel(modelId, ImmutableMap.of(MLModel.MODEL_STATE_FIELD, modelState));
                }
            }
        }
    }

    private void cleanUpLocalCacheFiles() {
        Path uploadModelRootPath = mlEngine.getUploadModelRootPath();
        Path loadModelRootPath = mlEngine.getLoadModelRootPath();
        Path modelCacheRootPath = mlEngine.getModelCacheRootPath();
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
        deleteFileQuietly(mlEngine.getModelCachePath(modelId));
        deleteFileQuietly(mlEngine.getLoadModelPath(modelId));
        deleteFileQuietly(mlEngine.getUploadModelPath(modelId));
    }
}
