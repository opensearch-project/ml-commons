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
import java.util.Optional;
import java.util.Set;

import org.opensearch.action.FailedNodeException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.nodes.TransportNodesAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.transport.sync.MLSyncUpAction;
import org.opensearch.ml.common.transport.sync.MLSyncUpInput;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodeResponse;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesRequest;
import org.opensearch.ml.common.transport.sync.MLSyncUpNodesResponse;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.engine.ModelHelper;
import org.opensearch.ml.engine.utils.FileUtils;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskCache;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

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

    private final MLModelCacheHelper mlModelCacheHelper;

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
        MLEngine mlEngine,
        MLModelCacheHelper mlModelCacheHelper
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
        this.mlModelCacheHelper = mlModelCacheHelper;

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

    private MLSyncUpNodeResponse createSyncUpNodeResponse(MLSyncUpNodesRequest syncUpNodesRequest) {
        MLSyncUpInput syncUpInput = syncUpNodesRequest.getSyncUpInput();
        Map<String, String[]> addedWorkerNodes = syncUpInput.getAddedWorkerNodes();
        Map<String, String[]> removedWorkerNodes = syncUpInput.getRemovedWorkerNodes();
        Map<String, Set<String>> modelRoutingTable = syncUpInput.getModelRoutingTable();
        Map<String, Set<String>> runningDeployModelTasks = syncUpInput.getRunningDeployModelTasks();
        // DeployToAllNodes will be created when model deployed on each worker nodes.
        // Only undeploy model and partial undeploy case will pass this deployToAllNodes map to update the cache deployToAllNodes value
        // and all values in this map is false.
        Map<String, Boolean> deployToAllNodes = syncUpInput.getDeployToAllNodes();

        if (addedWorkerNodes != null && addedWorkerNodes.size() > 0) {
            for (Map.Entry<String, String[]> entry : addedWorkerNodes.entrySet()) {
                mlModelManager.addModelWorkerNode(entry.getKey(), entry.getValue());
            }
        }
        if (removedWorkerNodes != null && removedWorkerNodes.size() > 0) {
            for (Map.Entry<String, String[]> entry : removedWorkerNodes.entrySet()) {
                mlModelManager
                    .removeModelWorkerNode(
                        entry.getKey(),
                        Optional.ofNullable(deployToAllNodes).orElse(Map.of()).containsKey(entry.getKey()),
                        entry.getValue()
                    );
            }
        }

        String[] deployedModelIds = null;
        String[] runningDeployModelTaskIds = null;
        String[] runningDeployModelIds = null;
        if (syncUpInput.isGetDeployedModels()) {
            deployedModelIds = mlModelManager.getLocalDeployedModels();
            List<String[]> localRunningDeployModel = mlTaskManager.getLocalRunningDeployModelTasks();
            runningDeployModelTaskIds = localRunningDeployModel.get(0);
            runningDeployModelIds = localRunningDeployModel.get(1);
        }

        if (syncUpInput.isClearRoutingTable()) {
            mlModelManager.clearRoutingTable();
        } else if (modelRoutingTable != null) {
            for (Map.Entry<String, Set<String>> entry : modelRoutingTable.entrySet()) {
                log.debug("latest routing table for model: {}:  {}", entry.getKey(), entry.getValue().toArray(new String[0]));
            }
            mlModelManager.syncModelWorkerNodes(modelRoutingTable);
        }

        cleanUpLocalCache(runningDeployModelTasks);
        cleanUpLocalCacheFiles();

        return new MLSyncUpNodeResponse(
            clusterService.localNode(),
            "ok",
            deployedModelIds,
            runningDeployModelIds,
            runningDeployModelTaskIds
        );
    }

    // VisibleForTesting
    void cleanUpLocalCache(Map<String, Set<String>> runningDeployModelTasks) {
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
                if (mlTask.getTaskType() == MLTaskType.DEPLOY_MODEL
                    && mlTask.getState() == MLTaskState.CREATED
                    && runningDeployModelTasks != null
                    && runningDeployModelTasks.containsKey(taskId)) {
                    continue;
                }
                mlTaskManager
                    .updateMLTask(
                        taskId,
                        Map.of(MLTask.STATE_FIELD, MLTaskState.FAILED, MLTask.ERROR_FIELD, "timeout after " + mlTaskTimeout + " seconds"),
                        10_000,
                        true
                    );
            }
        }
    }

    private void cleanUpLocalCacheFiles() {
        Path registerModelRootPath = mlEngine.getRegisterModelRootPath();
        Path deployModelRootPath = mlEngine.getDeployModelRootPath();
        Path modelCacheRootPath = mlEngine.getModelCacheRootPath();
        Set<String> modelsInCacheFolder = FileUtils.getFileNames(registerModelRootPath, deployModelRootPath, modelCacheRootPath);
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
        deleteFileQuietly(mlEngine.getDeployModelPath(modelId));
        deleteFileQuietly(mlEngine.getRegisterModelPath(modelId));
    }
}
