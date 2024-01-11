/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.model;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.DoubleStream;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.opensearch.common.util.TokenBucket;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.engine.MLExecutable;
import org.opensearch.ml.engine.Predictable;
import org.opensearch.ml.profile.MLPredictRequestStats;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLModelCache {
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) MLModelState modelState;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) FunctionName functionName;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Predictable predictor;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) MLExecutable executor;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) TokenBucket modelRateLimiter;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Map<String, TokenBucket> userRateLimiterMap;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Boolean isModelEnabled;
    private final Set<String> targetWorkerNodes;
    private final Set<String> workerNodes;
    private MLModel modelInfo;
    private final Queue<Double> modelInferenceDurationQueue;
    private final Queue<Double> predictRequestDurationQueue;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Long memSizeEstimationCPU;
    private @Setter(AccessLevel.PROTECTED) @Getter(AccessLevel.PROTECTED) Long memSizeEstimationGPU;

    // In rare case, this could be null, e.g. model info not synced up yet a predict request comes in.
    @Setter
    private Boolean deployToAllNodes;

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

    /**
     * There are two entrance of this method, one is cron job sync up, another is undeploy action sync up.
     * Removing nodeId in targetWorkerNodes as well if deploy to all nodes is true or request is from undeploy.
     * Case1(request from cron job): A node dropped from the cluster, we regard the cluster a new cluster.
     * Cronjob will update the new planning worker nodes to model index(removed the dropped nodeId),
     * the sync up request will update target worker nodes in cache to make sure data consistency.
     * Case2(undeploy action sync up): User use undeploy API to undeploy partial nodes of a model, in this case,
     * undeploy action will send sync up request to cluster, and we need to remove the nodeIds in cache to make sure
     * data consistency with model index, and we need to change the deployToAllNodes to false as well.
     * When it's not deployed to all nodes and not from undeploy , we should regard the cluster the old cluster,
     * Cronjob will not update new planning worker nodes and here we don't update target worker nodes either.
     * @param nodeId
     * @param isFromUndeploy
     */
    public void removeWorkerNode(String nodeId, boolean isFromUndeploy) {
        if (this.isDeployToAllNodes() || isFromUndeploy) {
            targetWorkerNodes.remove(nodeId);
        }
        if (isFromUndeploy)
            deployToAllNodes = false;
        workerNodes.remove(nodeId);
        // when the model is not deployed to any node, we should remove the modelInfo from cache
        if (targetWorkerNodes.isEmpty() || workerNodes.isEmpty()) {
            modelInfo = null;
        }
    }

    public void removeWorkerNodes(Set<String> removedNodes, boolean isFromUndeploy) {
        if (this.isDeployToAllNodes() || isFromUndeploy) {
            targetWorkerNodes.removeAll(removedNodes);
        }
        if (isFromUndeploy)
            deployToAllNodes = false;
        workerNodes.removeAll(removedNodes);
        if (targetWorkerNodes.isEmpty() || workerNodes.isEmpty()) {
            modelInfo = null;
        }
    }

    /**
     * When a model's deployToAllNodes is true but auto deploy is not enabled.
     * New ml node joins cluster, the new node will not be deployed with model, but in Cron job the new node will be regards as
     * a planning worker node and the model status is PARTIALLY_DEPLOYED, if we don't update here, the model status in model index
     * and profile API will be not consistent.
     * @param nodeId
     */
    public void addWorkerNode(String nodeId) {
        if (this.isDeployToAllNodes()) {
            targetWorkerNodes.add(nodeId);
        }
        workerNodes.add(nodeId);
    }

    public String[] getWorkerNodes() {
        return workerNodes.toArray(new String[0]);
    }

    public void setModelInfo(MLModel modelInfo) {
        this.modelInfo = modelInfo;
    }

    public MLModel getCachedModelInfo() {
        return modelInfo;
    }

    public void syncWorkerNode(Set<String> workerNodes) {
        this.workerNodes.clear();
        this.workerNodes.addAll(workerNodes);
    }

    public boolean isDeployToAllNodes() {
        return this.deployToAllNodes != null && this.deployToAllNodes;
    }

    public void clearWorkerNodes() {
        workerNodes.clear();
    }

    public void clear() {
        modelState = null;
        functionName = null;
        workerNodes.clear();
        modelInfo = null;
        modelInferenceDurationQueue.clear();
        predictRequestDurationQueue.clear();
        if (predictor != null) {
            predictor.close();
        }
        memSizeEstimationCPU = 0L;
        memSizeEstimationGPU = 0L;
        if (executor != null) {
            executor.close();
        }
        isModelEnabled = null;
        modelRateLimiter = null;
        userRateLimiterMap = null;
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

            final double[] doubles = doubleStream.toArray();
            DescriptiveStatistics stats = new DescriptiveStatistics(doubles);
            statsBuilder.count(stats.getN());
            statsBuilder.max(stats.getMax());
            statsBuilder.min(stats.getMin());
            statsBuilder.average(stats.getMean());
            statsBuilder.p50(stats.getPercentile(50));
            statsBuilder.p90(stats.getPercentile(90));
            statsBuilder.p99(stats.getPercentile(99));

            return statsBuilder.build();
        }
        return null;
    }

    public boolean isValidCache() {
        return modelState != null || workerNodes.size() > 0;
    }
}
