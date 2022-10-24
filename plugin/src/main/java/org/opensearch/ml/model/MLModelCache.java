/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import java.util.DoubleSummaryStatistics;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.DoubleStream;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLPredictRequestStats;

import com.google.common.math.Quantiles;

@Log4j2
public class MLModelCache {
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) MLModelState modelState;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) FunctionName functionName;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Predictable predictor;
    private final Set<String> workerNodes;
    private final Queue<Double> inferenceDurationQueue;

    public MLModelCache() {
        workerNodes = ConcurrentHashMap.newKeySet();
        inferenceDurationQueue = new ConcurrentLinkedQueue<>();
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
        inferenceDurationQueue.clear();
        if (predictor != null) {
            predictor.close();
        }
    }

    public void addInferenceDuration(double duration, long maxRequestCount) {
        while (inferenceDurationQueue.size() >= maxRequestCount) {
            inferenceDurationQueue.poll();
        }
        this.inferenceDurationQueue.add(duration);
    }

    public MLPredictRequestStats getInferenceStats() {
        if (inferenceDurationQueue.size() > 0) {
            MLPredictRequestStats.MLPredictRequestStatsBuilder statsBuilder = MLPredictRequestStats.builder();
            DoubleStream doubleStream = inferenceDurationQueue.stream().mapToDouble(v -> v);
            DoubleSummaryStatistics doubleSummaryStatistics = doubleStream.summaryStatistics();
            statsBuilder.count(doubleSummaryStatistics.getCount());
            statsBuilder.max(doubleSummaryStatistics.getMax());
            statsBuilder.min(doubleSummaryStatistics.getMin());
            statsBuilder.average(doubleSummaryStatistics.getAverage());

            Quantiles.Scale percentiles = Quantiles.percentiles();
            statsBuilder.p50(percentiles.index(50).compute(inferenceDurationQueue));
            statsBuilder.p90(percentiles.index(90).compute(inferenceDurationQueue));
            statsBuilder.p99(percentiles.index(99).compute(inferenceDurationQueue));

            return statsBuilder.build();
        }
        return null;
    }

    public boolean isValidCache() {
        return modelState != null || workerNodes.size() > 0;
    }
}
