/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.DoubleStream;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLPredictRequestStats;

import com.google.common.math.Quantiles;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelCache {
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) MLModelState modelState;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) FunctionName functionName;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Predictable predictor;
    private final Set<String> targetWorkerNodes;
    private final Set<String> workerNodes;
    private final Queue<Double> modelInferenceDurationQueue;
    private final Queue<Double> predictRequestDurationQueue;

    public MLModelCache() {
        targetWorkerNodes = ConcurrentHashMap.newKeySet();
        workerNodes = ConcurrentHashMap.newKeySet();
        modelInferenceDurationQueue = new ConcurrentLinkedQueue<>();
        predictRequestDurationQueue = new ConcurrentLinkedQueue<>();
    }

    public void setTargetWorkerNodes(List<String> targetWorkerNodes) {
        if (targetWorkerNodes == null || targetWorkerNodes.size() == 0) {
            throw new IllegalArgumentException("Null or empty target worker nodes");
        }
        this.targetWorkerNodes.clear();
        this.targetWorkerNodes.addAll(targetWorkerNodes);
    }

    public String[] getTargetWorkerNodes() {
        return targetWorkerNodes.toArray(new String[0]);
    }

    public void removeWorkerNode(String nodeId) {
        workerNodes.remove(nodeId);
    }

    public void removeWorkerNodes(Set<String> removedNodes) {
        workerNodes.removeAll(removedNodes);
    }

    public void addWorkerNode(String nodeId) {
        workerNodes.add(nodeId);
    }

    public String[] getWorkerNodes() {
        return workerNodes.toArray(new String[0]);
    }

    public void syncWorkerNode(Set<String> workerNodes) {
        this.workerNodes.clear();
        this.workerNodes.addAll(workerNodes);
    }

    public void clearWorkerNodes() {
        workerNodes.clear();
    }

    public void clear() {
        modelState = null;
        functionName = null;
        workerNodes.clear();
        modelInferenceDurationQueue.clear();
        predictRequestDurationQueue.clear();
        if (predictor != null) {
            predictor.close();
        }
    }

    public void addModelInferenceDuration(double duration, long maxRequestCount) {
        addInferenceDuration(duration, maxRequestCount, modelInferenceDurationQueue);
    }

    public void addPredictRequestDuration(double duration, long maxRequestCount) {
        addInferenceDuration(duration, maxRequestCount, predictRequestDurationQueue);
    }

    private void addInferenceDuration(double duration, long maxRequestCount, Queue<Double> queue) {
        resizeInferenceQueue(maxRequestCount, queue);
        if (maxRequestCount > 0) {
            queue.add(duration);
        }
    }

    public void resizeMonitoringQueue(long maxRequestCount) {
        log.debug("resize inference duration monitoring queue with size {}", maxRequestCount);
        resizeInferenceQueue(maxRequestCount, predictRequestDurationQueue);
        resizeInferenceQueue(maxRequestCount, modelInferenceDurationQueue);
    }

    private void resizeInferenceQueue(long maxRequestCount, Queue<Double> queue) {
        if (maxRequestCount <= 0) {
            queue.clear();
        } else {
            while (queue.size() >= maxRequestCount) {
                queue.poll();
            }
        }
    }

    public MLPredictRequestStats getInferenceStats(boolean modelInference) {
        Queue<Double> queue = modelInference ? modelInferenceDurationQueue : predictRequestDurationQueue;
        if (queue.size() > 0) {
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

            return statsBuilder.build();
        }
        return null;
    }

    public boolean isValidCache() {
        return modelState != null || workerNodes.size() > 0;
    }
}
