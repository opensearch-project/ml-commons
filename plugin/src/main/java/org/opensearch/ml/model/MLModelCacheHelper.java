/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.model.MLGuard;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.MLExecutable;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLModelProfile;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelCacheHelper {
    private final Map<String, MLModelCache> modelCaches;

    private final Map<String, MLModel> autoDeployModels;
    private volatile Long maxRequestCount;

    public MLModelCacheHelper(ClusterService clusterService, Settings settings) {
        this.modelCaches = new ConcurrentHashMap<>();
        this.autoDeployModels = new ConcurrentHashMap<>();

        maxRequestCount = ML_COMMONS_MONITORING_REQUEST_COUNT.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MONITORING_REQUEST_COUNT, it -> maxRequestCount = it);
    }

    /**
     * Initialize model state.
     * 
     * @param modelId      model id
     * @param state        model state
     * @param functionName function name
     */
    public synchronized void initModelState(
        String modelId,
        MLModelState state,
        FunctionName functionName,
        List<String> targetWorkerNodes,
        boolean deployToAllNodes
    ) {
        if (isModelRunningOnNode(modelId) && !isAutoDeploying(modelId)) {
            throw new MLLimitExceededException("Duplicate deploy model task");
        }
        log.debug("init model state for model {}, state: {}", modelId, state);
        MLModelCache modelCache = new MLModelCache();
        modelCache.setModelState(state);
        modelCache.setFunctionName(functionName);
        modelCache.setTargetWorkerNodes(targetWorkerNodes);
        modelCache.setDeployToAllNodes(deployToAllNodes);
        modelCache.setLastAccessTime(Instant.now());
        modelCaches.put(modelId, modelCache);
    }

    public synchronized void initModelStateAutoDeploy(
        String modelId,
        MLModelState state,
        FunctionName functionName,
        List<String> targetWorkerNodes
    ) {
        log.debug("init local model deployment state for model {}, state: {}", modelId, state);
        if (isModelRunningOnNode(modelId)) {
            // model state initialized
            return;
        }
        MLModelCache modelCache = new MLModelCache();
        modelCache.setModelState(state);
        modelCache.setFunctionName(functionName);
        modelCache.setTargetWorkerNodes(targetWorkerNodes);
        modelCache.setDeployToAllNodes(false);
        modelCache.setLastAccessTime(Instant.now());
        modelCaches.put(modelId, modelCache);
        setIsAutoDeploying(modelId, true);
    }

    /**
     * Set model state
     * 
     * @param modelId model id
     * @param state   model state
     */
    public synchronized void setModelState(String modelId, MLModelState state) {
        log.debug("Updating State of Model {}  to state {}", modelId, state);
        getExistingModelCache(modelId).setModelState(state);
    }

    /**
     * Set a rate limiter to enable model level throttling
     * 
     * @param modelId     model id
     * @param rateLimiter rate limiter
     */
    public synchronized void setRateLimiter(String modelId, TokenBucket rateLimiter) {
        log.debug("Setting the rate limiter for Model {}", modelId);
        getExistingModelCache(modelId).setRateLimiter(rateLimiter);
    }

    /**
     * Get the current rate limiter for the model.
     *
     * @param modelId model id
     */
    public TokenBucket getRateLimiter(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getRateLimiter();
    }

    /**
     * Remove the rate limiter from cache to disable model level throttling
     * 
     * @param modelId model id
     */
    public synchronized void removeRateLimiter(String modelId) {
        log.debug("Removing the rate limiter for Model {}", modelId);
        getExistingModelCache(modelId).setRateLimiter(null);
    }

    /**
     * Set the user rate limiter map to enable user level throttling.
     *
     * @param modelId            model id
     * @param userRateLimiterMap a map with user's name and its corresponding rate
     *                           limiter
     */
    public synchronized void setUserRateLimiterMap(String modelId, Map<String, TokenBucket> userRateLimiterMap) {
        log.debug("Setting the user level rate limiter for Model {}", modelId);
        getExistingModelCache(modelId).setUserRateLimiterMap(userRateLimiterMap);
    }

    /**
     * Remove the user rate limiter map from cache to disable user level throttling.
     *
     * @param modelId model id
     */
    public synchronized void removeUserRateLimiterMap(String modelId) {
        log.debug("Removing the user level rate limiter for Model {}", modelId);
        getExistingModelCache(modelId).setUserRateLimiterMap(null);
    }

    /**
     * Get the current user and its corresponding rate limiter map for the model
     *
     * @param modelId model id
     */
    public Map<String, TokenBucket> getUserRateLimiterMap(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getUserRateLimiterMap();
    }

    /**
     * Get the rate limiter for a specific user for the model
     *
     * @param modelId model id
     */
    public TokenBucket getUserRateLimiter(String modelId, String user) {
        Map<String, TokenBucket> userRateLimiterMap = getUserRateLimiterMap(modelId);
        if (userRateLimiterMap == null) {
            return null;
        }
        return userRateLimiterMap.get(user);
    }

    /**
     * Set the ml interface for the model
     *
     * @param modelId model id
     * @param modelInterface model interface
     */
    public synchronized void setModelInterface(String modelId, Map<String, String> modelInterface) {
        log.debug("Setting ML Interface {} for Model {}", modelInterface, modelId);
        getExistingModelCache(modelId).setModelInterface(modelInterface);
    }

    /**
     * Get the current ml interface for the model
     *
     * @param modelId model id
     */
    public Map<String, String> getModelInterface(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getModelInterface();
    }

    /**
     * Remove the ml interface from cache
     *
     * @param modelId model id
     */
    public synchronized void removeModelInterface(String modelId) {
        log.debug("Removing the ML Interface from Model {}", modelId);
        getExistingModelCache(modelId).setModelInterface(null);
    }

    /**
     * Set a ml guard
     *
     * @param modelId model id
     * @param mlGuard mlGuard
     */
    public synchronized void setMLGuard(String modelId, MLGuard mlGuard) {
        log.debug("Setting ML guard {} for Model {}", mlGuard, modelId);
        getExistingModelCache(modelId).setMlGuard(mlGuard);
    }

    /**
     * Get the current ML guard for the model.
     *
     * @param modelId model id
     */
    public MLGuard getMLGuard(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getMlGuard();
    }

    /**
     * Remove the ML guard from cache
     *
     * @param modelId model id
     */
    public synchronized void removeMLGuard(String modelId) {
        log.debug("Removing the ML guard from Model {}", modelId);
        getExistingModelCache(modelId).setMlGuard(null);
    }

    /**
     * Set a quota flag to control if the model can still receive request
     * 
     * @param modelId        model id
     * @param isModelEnabled quota flag
     */
    public synchronized void setIsModelEnabled(String modelId, Boolean isModelEnabled) {
        log.debug("Setting the quota flag for Model {}", modelId);
        getExistingModelCache(modelId).setIsModelEnabled(isModelEnabled);
    }

    /**
     * Get the current quota flag condition for the model
     * 
     * @param modelId model id
     */
    public Boolean getIsModelEnabled(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getIsModelEnabled();
    }

    /**
     * Set a flag to show if model is in auto deploying status
     *
     * @param modelId        model id
     * @param isModelAutoDeploying auto deploy flag
     */
    public synchronized void setIsAutoDeploying(String modelId, Boolean isModelAutoDeploying) {
        log.debug("Setting the auto deploying flag for Model {}", modelId);
        getExistingModelCache(modelId).setIsAutoDeploying(isModelAutoDeploying);
    }

    /**
     * Check if model is in auto deploying.
     *
     * @param modelId model id
     * @return true if model is auto deploying.
     */
    public boolean isAutoDeploying(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        return modelCache != null && BooleanUtils.isTrue(modelCache.getIsAutoDeploying());
    }

    /**
     * Set memory size estimation CPU/GPU
     * 
     * @param modelId model id
     * @param format  model format like onnx
     * @param size    memory size
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
     * 
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
     * 
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
     * 
     * @param modelId model id
     * @return true if model deployed
     */
    public synchronized boolean isModelDeployed(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        return modelCache != null && modelCache.getModelState() == MLModelState.DEPLOYED;
    }

    /**
     * Get deployed models on node.
     * 
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
     * 
     * @return array of model id
     */
    public String[] getLocalDeployedModels() {
        return modelCaches
            .entrySet()
            .stream()
            .filter(
                entry -> (entry.getValue().getModelState() == MLModelState.DEPLOYED
                    && entry.getValue().getFunctionName() != FunctionName.REMOTE)
            )
            .map(entry -> entry.getKey())
            .collect(Collectors.toList())
            .toArray(new String[0]);
    }

    /**
     * Get expired models on node.
     *
     * @return array of expired model id
     */
    public String[] getExpiredModels() {
        return modelCaches.entrySet().stream().filter(entry -> {
            MLModelCache modelCache = entry.getValue();
            MLModel mlModel = modelCache.getCachedModelInfo();
            MLModelState modelState = modelCache.getModelState();
            if (mlModel == null || mlModel.getDeploySetting() == null) {
                return false; // no TTL, never expire
            }
            Duration liveDuration = Duration.between(entry.getValue().getLastAccessTime(), Instant.now());
            Long ttlInMinutes = mlModel.getDeploySetting().getModelTTLInMinutes();
            if (ttlInMinutes < 0) {
                return false;
            }
            Duration ttl = Duration.ofMinutes(ttlInMinutes);
            boolean isModelExpired = liveDuration.getSeconds() >= ttl.getSeconds();
            return isModelExpired && (modelState == MLModelState.DEPLOYED || modelState == MLModelState.PARTIALLY_DEPLOYED);
        }).map(entry -> entry.getKey()).collect(Collectors.toList()).toArray(new String[0]);
    }

    /**
     * Check if model is running on node.
     * 
     * @param modelId model id
     * @return true if model is running on node.
     */
    public boolean isModelRunningOnNode(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        return modelCache != null && modelCache.getModelState() != null;
    }

    /**
     * Set predictor of model.
     * 
     * @param modelId   model id
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
     * 
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
     * 
     * @param modelId           model id
     * @param targetWorkerNodes target worker nodes of model
     */
    public void setTargetWorkerNodes(String modelId, List<String> targetWorkerNodes) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            modelCache.setTargetWorkerNodes(targetWorkerNodes);
        }
    }

    /**
     * Set the last access time to Instant.now()
     *
     * @param modelId           model id
     */
    public void refreshLastAccessTime(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        modelCache.setLastAccessTime(Instant.now());
    }

    /**
     * Remove model.
     * 
     * @param modelId model id
     */
    public void removeModel(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache != null) {
            log.debug("removing model {} from cache", modelId);
            modelCache.clear();
            modelCaches.remove(modelId);
        }
        autoDeployModels.remove(modelId);
    }

    /**
     * Get all model IDs in model cache.
     * 
     * @return array of model id
     */
    public String[] getAllModels() {
        return modelCaches.keySet().toArray(new String[0]);
    }

    /**
     * Get worker nodes of model.
     * 
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
     * Get target worker nodes of model.
     *
     * @param modelId model id
     * @return array of node id; return null if model not exists in cache
     */
    public String[] getTargetWorkerNodes(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return null;
        }
        return modelCache.getTargetWorkerNodes();
    }

    /**
     * Add worker node of model.
     * 
     * @param modelId model id
     * @param nodeId  node id
     */
    public synchronized void addWorkerNode(String modelId, String nodeId) {
        log.debug("add node {} to model routing table for model: {}", nodeId, modelId);
        MLModelCache modelCache = getOrCreateModelCache(modelId);
        modelCache.addWorkerNode(nodeId);
    }

    /**
     * Remove worker nodes for all models.
     * 
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
     * 
     * @param modelId        model id
     * @param nodeId         node id
     * @param isFromUndeploy Only allow custom deploy is true and user undeployed
     *                       partial nodes, the isFromUndeploy is true, in
     *                       this case, we need to change the deployToAllNodes flag
     *                       to false in cache to make sure it's consistent
     *                       with model index, also we need to change the target
     *                       worker nodes to exclude the removed worker nodes.
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
     * 
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
     * Sync planning worker nodes for all models.
     *
     * @param modelPlanningWorkerNodes planning worker nodes of all models
     */
    public void syncPlanningWorkerNodes(Map<String, Set<String>> modelPlanningWorkerNodes) {
        log.debug("sync model planning worker nodes");
        modelPlanningWorkerNodes.entrySet().forEach(entry -> {
            MLModelCache modelCache = getOrCreateModelCache(entry.getKey());
            modelCache.syncPlanningWorkerNodes(entry.getValue());
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
     * 
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
     * 
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
     * 
     * @param modelId  model id
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
     * 
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

    public void setModelInfo(String modelId, MLModel mlModel) {
        MLModelCache mlModelCache = modelCaches.get(modelId);
        if (mlModelCache != null) {
            mlModelCache.setModelInfo(mlModel);
        }
    }

    public MLModel getModelInfo(String modelId) {
        MLModelCache mlModelCache = modelCaches.get(modelId);
        if (mlModelCache == null) {
            return null;
        }
        return mlModelCache.getCachedModelInfo();
    }

    private MLModelCache getExistingModelCache(String modelId) {
        MLModelCache modelCache = modelCaches.get(modelId);
        if (modelCache == null) {
            return getOrCreateModelCache(modelId);
            // throw new IllegalArgumentException("Model not found in cache");
        }
        return modelCache;
    }

    private MLModelCache getOrCreateModelCache(String modelId) {
        return modelCaches.computeIfAbsent(modelId, it -> new MLModelCache());
    }

    public MLModel addModelToAutoDeployCache(String modelId, MLModel model) {
        MLModel addedModel = autoDeployModels.computeIfAbsent(modelId, key -> model);
        if (addedModel == model) {
            log.info("Add model {} to auto deploy cache", modelId);
        }
        return addedModel;
    }

    public void removeAutoDeployModel(String modelId) {
        MLModel removedModel = autoDeployModels.remove(modelId);
        if (removedModel != null) {
            log.info("Remove model {} from auto deploy cache", modelId);
        }
    }
}
