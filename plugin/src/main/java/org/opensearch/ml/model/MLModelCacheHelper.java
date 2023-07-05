/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.MLExecutable;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLModelProfile;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelCacheHelper {
    private final Map<String, MLModelCache> modelCaches;
    private volatile Long maxRequestCount;

    public MLModelCacheHelper(ClusterService clusterService, Settings settings) {
        this.modelCaches = new ConcurrentHashMap<>();

        maxRequestCount = ML_COMMONS_MONITORING_REQUEST_COUNT.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MONITORING_REQUEST_COUNT, it -> maxRequestCount = it);
    }

    /**
     * Initialize model state.
     * @param modelId model id
     * @param state model state
     * @param functionName function name
     */
    public synchronized void initModelState(
        String modelId,
        MLModelState state,
        FunctionName functionName,
        List<String> targetWorkerNodes,
        boolean deployToAllNodes
    ) {
        if (isModelRunningOnNode(modelId)) {
            throw new MLLimitExceededException("Duplicate deploy model task");
        }
        log.debug("init model state for model {}, state: {}", modelId, state);
        MLModelCache modelCache = new MLModelCache();
        modelCache.setModelState(state);
        modelCache.setFunctionName(functionName);
        modelCache.setTargetWorkerNodes(targetWorkerNodes);
        modelCache.setDeployToAllNodes(deployToAllNodes);
        modelCaches.put(modelId, modelCache);
    }

    /**
     * Set model state
     * @param modelId model id
     * @param state model state
     */
    public synchronized void setModelState(String modelId, MLModelState state) {
        log.debug("Updating State of Model {}  to state {}", modelId, state);
        getExistingModelCache(modelId).setModelState(state);
    }

    /**
     * Set memory size estimation CPU/GPU
     * @param modelId model id
     * @param format model format like onnx
     * @param size memory size
     */
    public synchronized void setMemSizeEstimation(String modelId, MLModelFormat format, Long size) {
        Long memSize = getMemSizeEstimation(format, size);
        log.debug("Updating memSizeEstimation of Model {}  to {}", modelId, memSize);
        getExistingModelCache(modelId).setMemSizeEstimationCPU(memSize);
        getExistingModelCache(modelId).setMemSizeEstimationGPU(memSize);
    }

    private Long getMemSizeEstimation(MLModelFormat format, Long size) {
        Double scale = 1.0;
        switch (format) {
            case ONNX:
                scale = 1.5;
                break;
            case TORCH_SCRIPT:
                scale = 1.2;
                break;
        }
        Long memSize = Double.valueOf(scale * size).longValue();
        return memSize;
    }

    /**
     * Get CPU memory estimation.
     * @param modelId model id
     * @return Long
     */
    public Long getMemEstCPU(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getMemSizeEstimationCPU();
    }

    /**
     * Get GPU memory estimation.
     * @param modelId model id
     * @return Long
     */
    public Long getMemEstGPU(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getMemSizeEstimationGPU();
    }

    /**
     * Check if model deployed on node.
     * @param modelId model id
     * @return true if model deployed
     */
    public synchronized boolean isModelDeployed(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        return modelCache != null && modelCache.getModelState() == MLModelState.DEPLOYED;
    }

    /**
     * Get deployed models on node.
     * @return array of model id
     */
    public String[] getDeployedModels() {
        return modelCaches
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().getModelState() == MLModelState.DEPLOYED)
            .map(entry -> entry.getKey())
            .collect(Collectors.toList())
            .toArray(new String[0]);
    }

    /**
     * Get deployed local models on node.
     * @return array of model id
     */
    public String[] getLocalDeployedModels() {
        return modelCaches
                .entrySet()
                .stream()
                .filter(entry -> (entry.getValue().getModelState() == MLModelState.DEPLOYED && entry.getValue().getFunctionName() != FunctionName.REMOTE))
                .map(entry -> entry.getKey())
                .collect(Collectors.toList())
                .toArray(new String[0]);
    }

    /**
     * Check if model is running on node.
     * @param modelId model id
     * @return true if model is running on node.
     */
    public boolean isModelRunningOnNode(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        return modelCache != null && modelCache.getModelState() != null;
    }

    /**
     * Set predictor of model.
     * @param modelId model id
     * @param predictor predictor
     */
    public synchronized void setPredictor(String modelId, Predictable predictor) {
        MLModelCache modelCache = getExistingModelCache(modelId);
        modelCache.setPredictor(predictor);
    }

    public synchronized void setMLExecutor(String modelId, MLExecutable mlExecutor) {
        MLModelCache modelCache = getExistingModelCache(modelId);
        modelCache.setExecutor(mlExecutor);
    }

    public MLExecutable getMLExecutor(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getExecutor();
    }

    /**
     * Get predictor of model.
     * @param modelId model id
     * @return predictor
     */
    public Predictable getPredictor(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getPredictor();
    }

    /**
     * Set target worker nodes of model.
     * @param modelId model id
     * @param targetWorkerNodes target worker nodes of model
     */
    public void setTargetWorkerNodes(String modelId, List<String> targetWorkerNodes) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            modelCache.setTargetWorkerNodes(targetWorkerNodes);
        }
    }

    /**
     * Remove model.
     * @param modelId model id
     */
    public void removeModel(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            log.debug("removing model {} from cache", modelId);
            modelCache.clear();
            modelCaches.remove(modelId);
        }
    }

    /**
     * Get all model IDs in model cache.
     * @return array of model id
     */
    public String[] getAllModels() {
        return modelCaches.keySet().toArray(new String[0]);
    }

    /**
     * Get worker nodes of model.
     * @param modelId model id
     * @return array of node id; return null if model not exists in cache
     */
    public String[] getWorkerNodes(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getWorkerNodes();
    }

    /**
     * Add worker node of model.
     * @param modelId model id
     * @param nodeId node id
     */
    public synchronized void addWorkerNode(String modelId, String nodeId) {
        log.debug("add node {} to model routing table for model: {}", nodeId, modelId);
        MLModelCache modelCache = getOrCreateModelCache(modelId);
        modelCache.addWorkerNode(nodeId);
    }

    /**
     * Remove worker nodes for all models.
     * @param removedNodes removed nodes
     */
    public void removeWorkerNodes(Set<String> removedNodes, boolean isFromUndeploy) {
        Set<String> modelIds = modelCaches.keySet();
        for (String modelId : modelIds) {
            MLModelCache modelCache = modelCaches.get(modelId);
            log.debug("remove worker nodes of model {} : {}", modelId, removedNodes.toArray(new String[0]));
            modelCache.removeWorkerNodes(removedNodes, isFromUndeploy);
            if (!modelCache.isValidCache()) {
                log.debug("remove model cache {}", modelId);
                modelCaches.remove(modelId);
            }
        }
    }

    /**
     * Remove worker node of model.
     * @param modelId model id
     * @param nodeId node id
     * @param isFromUndeploy Only allow custom deploy is true and user undeployed partial nodes, the isFromUndeploy is true, in
     *                       this case, we need to change the deployToAllNodes flag to false in cache to make sure it's consistent
     *                       with model index, also we need to change the target worker nodes to exclude the removed worker nodes.
     */
    public void removeWorkerNode(String modelId, String nodeId, boolean isFromUndeploy) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            log.debug("remove worker node {} of model {} from cache", nodeId, modelId);
            modelCache.removeWorkerNode(nodeId, isFromUndeploy);
            if (!modelCache.isValidCache()) {
                log.debug("remove model {} from cache as no node running it", modelId);
                modelCaches.remove(modelId);
            }
        }
    }

    /**
     * Sync worker nodes for all models.
     * @param modelWorkerNodes worker nodes of all models
     */
    public void syncWorkerNodes(Map<String, Set<String>> modelWorkerNodes) {
        log.debug("sync model worker nodes");
        Set<String> currentModels = new HashSet(this.modelCaches.keySet());
        currentModels.removeAll(modelWorkerNodes.keySet());
        if (currentModels.size() > 0) {
            currentModels.forEach(modelId -> clearWorkerNodes(modelId));
        }
        modelWorkerNodes.entrySet().forEach(entry -> {
            MLModelCache modelCache = getOrCreateModelCache(entry.getKey());
            modelCache.syncWorkerNode(entry.getValue());
        });
    }

    /**
     * Clear worker nodes for all models.
     */
    public void clearWorkerNodes() {
        log.debug("clear all model worker nodes");
        modelCaches.entrySet().forEach(entry -> clearWorkerNodes(entry.getKey()));
    }

    /**
     * Clear worker node of model.
     * @param modelId model id
     */
    public void clearWorkerNodes(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            log.debug("clear worker nodes of model {}", modelId);
            modelCache.clearWorkerNodes();
            if (!modelCache.isValidCache()) {
                modelCaches.remove(modelId);
            }
        }
    }

    /**
     * Get model profile.
     * @param modelId model id
     * @return model profile
     */
    public MLModelProfile getModelProfile(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }

        MLModelProfile.MLModelProfileBuilder builder = MLModelProfile.builder();
        builder.modelState(modelCache.getModelState());
        if (modelCache.getPredictor() != null) {
            builder.predictor(modelCache.getPredictor().toString());
        }
        String[] targetWorkerNodes = modelCache.getTargetWorkerNodes();
        if (targetWorkerNodes.length > 0) {
            builder.targetWorkerNodes(targetWorkerNodes);
        }
        String[] workerNodes = modelCache.getWorkerNodes();
        if (workerNodes.length > 0) {
            builder.workerNodes(workerNodes);
        }
        builder.modelInferenceStats(modelCache.getInferenceStats(true));
        builder.predictRequestStats(modelCache.getInferenceStats(false));
        builder.memSizeEstimationCPU(modelCache.getMemSizeEstimationCPU());
        builder.memSizeEstimationGPU(modelCache.getMemSizeEstimationGPU());
        return builder.build();
    }

    /**
     * Add model inference duration.
     * @param modelId model id
     * @param duration time in milliseconds used to run inference.
     */
    public void addModelInferenceDuration(String modelId, double duration) {
        MLModelCache modelCache = getOrCreateModelCache(modelId);
        modelCache.addModelInferenceDuration(duration, maxRequestCount);
    }

    public void addPredictRequestDuration(String modelId, double duration) {
        MLModelCache modelCache = getOrCreateModelCache(modelId);
        modelCache.addPredictRequestDuration(duration, maxRequestCount);
    }

    public void resizeMonitoringQueue(long monitoringReqCount) {
        for (Map.Entry<String, MLModelCache> entry : modelCaches.entrySet()) {
            entry.getValue().resizeMonitoringQueue(monitoringReqCount);
        }
    }

    /**
     * Get function name of model
     * @param modelId model id
     * @return function name
     */
    public FunctionName getFunctionName(String modelId) {
        MLModelCache modelCache = getExistingModelCache(modelId);
        return modelCache.getFunctionName();
    }

    public Optional<FunctionName> getOptionalFunctionName(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        FunctionName functionName = modelCache == null ? null : modelCache.getFunctionName();
        return Optional.ofNullable(functionName);
    }

    public void setDeployToAllNodes(String modelId, Boolean deployToAllNodes) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            log.info("Starting to set deployToAllNodes flag to modelId: {}, value to: {}", modelId, deployToAllNodes);
            modelCache.setDeployToAllNodes(deployToAllNodes);
        }
    }

    public boolean getDeployToAllNodes(String modelId) {
        MLModelCache mlModelCache = getExistingModelCache(modelId);
        return mlModelCache.isDeployToAllNodes();
    }

    private MLModelCache getExistingModelCache(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            throw new IllegalArgumentException("Model not found in cache");
        }
        return modelCache;
    }

    private MLModelCache getOrCreateModelCache(String modelId) {
        return modelCaches.computeIfAbsent(modelId, it -> new MLModelCache());
    }

}
