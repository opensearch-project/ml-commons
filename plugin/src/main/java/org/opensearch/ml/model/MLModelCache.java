/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;

import java.util.DoubleSummaryStatistics;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.DoubleStream;

import lombok.extern.log4j.Log4j2;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLModelProfile;
import org.opensearch.ml.profile.MLPredictRequestStats;

import com.google.common.math.Quantiles;

@Log4j2
public class MLModelCache {
    private final ClusterService clusterService;

    /**
     * All model state. Contains both build-in algo model and custom model.
     */
    private final Map<String, MLModelState> modelStates;
    private final Map<String, Predictable> predictors;
    private final Map<String, Set<String>> modelRoutingTable;// routingTable
    private final Map<String, Queue<Double>> modelInferenceDuration;
    private final Map<String, FunctionName> modelFunctionNames;
    private volatile Long maxRequestCount;

    public MLModelCache(ClusterService clusterService, Settings settings) {
        this.clusterService = clusterService;
        this.modelStates = new ConcurrentHashMap<>();
        this.predictors = new ConcurrentHashMap<>();
        this.modelRoutingTable = new ConcurrentHashMap<>();
        this.modelInferenceDuration = new ConcurrentHashMap<>();
        this.modelFunctionNames = new ConcurrentHashMap<>();

        maxRequestCount = ML_COMMONS_MONITORING_REQUEST_COUNT.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MONITORING_REQUEST_COUNT, it -> maxRequestCount = it);
    }

    public synchronized boolean hasModel(String modelId) {
        return predictors.containsKey(modelId);
    }

    public synchronized boolean isModelLoaded(String modelId) {
        MLModelState mlModelState = modelStates.get(modelId);
        if (mlModelState == MLModelState.LOADED) {
            return true;
        }
        return false;
    }

    public synchronized void initModelState(String modelId, MLModelState state, FunctionName functionName) {
        if (modelStates.containsKey(modelId)) {
            throw new IllegalArgumentException("Duplicate model task");
        }
        modelStates.put(modelId, state);
        modelFunctionNames.put(modelId, functionName);
    }

    public synchronized void setModelState(String modelId, MLModelState state) {
        if (!modelStates.containsKey(modelId)) {
            throw new IllegalArgumentException("Model not found in cache");
        }
        modelStates.put(modelId, state);
    }

    public void removeModelState(String modelId) {
        modelStates.remove(modelId);
        modelFunctionNames.remove(modelId);
    }

    public void removeWorkNodes(Set<String> removedNodes) {
        for (Map.Entry<String, Set<String>> entry : modelRoutingTable.entrySet()) {
            Set<String> nodes = entry.getValue();
            nodes.removeAll(removedNodes);
        }
    }

    public synchronized void addPredictable(String modelId, Predictable predictable) {
        this.predictors.put(modelId, predictable);
    }

    public synchronized void addNodeToModelRoutingTable(String modelId, String nodeId) {
        if (!modelRoutingTable.containsKey(modelId)) {
            ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
            Set<String> set = map.newKeySet();
            modelRoutingTable.put(modelId, set);
        }
        log.debug("add node {} to model routing table for model: {}", nodeId, modelId);
        modelRoutingTable.get(modelId).add(nodeId);
    }

    public synchronized void removeNodeFromModelRoutingTable(String modelId, String nodeId) {
        if (!modelRoutingTable.containsKey(modelId)) {
            log.debug("model {} not found in cache", modelId);
            return;
        }
        log.debug("remove node {} from model routing table of model {}", nodeId, modelId);
        modelRoutingTable.get(modelId).remove(nodeId);
        if (modelRoutingTable.get(modelId).size() == 0) {
            log.debug("remove model {} from model routing table as no node running it", modelId);
            modelRoutingTable.remove(modelId);
        }
    }

    public void removeModel(String modelId) {
        this.modelStates.remove(modelId);
        this.modelFunctionNames.remove(modelId);
        modelInferenceDuration.remove(modelId);
        Predictable predictable = this.predictors.remove(modelId);
        if (predictable != null) {
            predictable.close();
        }
        log.debug("remove model state and predictable model {}", modelId);
        removeNodeFromModelRoutingTable(modelId, clusterService.localNode().getId());
    }

    public String[] getWorkerNodes(String modelId) {
        Set<String> nodes = modelRoutingTable.get(modelId);
        if (nodes == null) {
            return null;
        }
        return nodes.toArray(new String[0]);
    }

    public Predictable getPredictable(String modelId) {
        return predictors.get(modelId);
    }

    public synchronized int modelCount() {
        return modelStates.size();
    }

    public String[] getLoadedModels() {
        return predictors.keySet().toArray(new String[0]);
    }

    public void syncModelRouting(Map<String, Set<String>> modelRoutingTable) {
        log.debug("sync model routing for model");
        Set<String> currentModels = new HashSet(this.modelRoutingTable.keySet());
        this.modelRoutingTable.putAll(modelRoutingTable);
        currentModels.removeAll(modelRoutingTable.keySet());
        if (currentModels.size() > 0) {
            currentModels.forEach(k -> this.modelRoutingTable.remove(k));
        }
    }

    public void clearRoutingTable() {
        log.debug("clear routing table");
        this.modelRoutingTable.clear();
    }

    public String[] getAllModelIds() {
        Set<String> modelIds = new HashSet<>();
        modelIds.addAll(this.modelStates.keySet());
        modelIds.addAll(this.predictors.keySet());
        modelIds.addAll(this.modelRoutingTable.keySet());
        return modelIds.toArray(new String[0]);
    }

    public MLModelProfile getModelProfile(String modelId) {
        MLModelProfile.MLModelProfileBuilder builder = MLModelProfile.builder().modelState(modelStates.get(modelId));
        Predictable predictable = predictors.get(modelId);
        if (predictable != null) {
            builder.predictor(predictable.toString());
        }
        Set<String> nodes = modelRoutingTable.get(modelId);
        if (nodes != null && nodes.size() > 0) {
            builder.workerNodes(nodes.toArray(new String[0]));
        }
        Queue<Double> queue = modelInferenceDuration.get(modelId);
        if (queue != null && queue.size() > 0) {
            MLPredictRequestStats.MLPredictRequestStatsBuilder statsBuilder = MLPredictRequestStats.builder();
            DoubleStream doubleStream = queue.stream().mapToDouble(v -> v);
            DoubleSummaryStatistics doubleSummaryStatistics = doubleStream.summaryStatistics();
            statsBuilder.count(doubleSummaryStatistics.getCount());
            statsBuilder.max(doubleSummaryStatistics.getMax());
            statsBuilder.min(doubleSummaryStatistics.getMin());
            statsBuilder.average(doubleSummaryStatistics.getAverage());

            Quantiles.Scale percentiles = Quantiles.percentiles();
            statsBuilder.p50(percentiles.index(50).compute(queue));
            statsBuilder.p90(percentiles.index(90).compute(queue));
            statsBuilder.p99(percentiles.index(99).compute(queue));

            builder.predictStats(statsBuilder.build());
        }
        return builder.build();
    }

    public void addInferenceDuration(String modelId, double duration) {
        log.debug("add duration of model {}: {}ms", modelId, duration);
        Queue<Double> queue = modelInferenceDuration.computeIfAbsent(modelId, it -> new ConcurrentLinkedQueue<>());
        while (queue.size() >= maxRequestCount) {
            queue.poll();
        }
        queue.add(duration);
    }

    public FunctionName getModelFunctionName(String modelId) {
        return modelFunctionNames.get(modelId);
    }

    public boolean containsModel(String modelId) {
        return modelStates.containsKey(modelId);
    }
}
