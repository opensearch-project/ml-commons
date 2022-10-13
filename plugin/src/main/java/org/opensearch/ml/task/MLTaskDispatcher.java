/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TASK_DISPATCH_POLICY;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.naming.LimitExceededException;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.stats.MLNodeLevelStat;

import com.google.common.collect.ImmutableSet;

/**
 * MLTaskDispatcher is responsible for dispatching the ml tasks.
 * TODO: Add more test
 */
@Log4j2
public class MLTaskDispatcher {
    // todo: move to a config class
    private final short DEFAULT_JVM_HEAP_USAGE_THRESHOLD = 85;
    private final ClusterService clusterService;
    private final Client client;
    private AtomicInteger nextNode;
    private volatile Integer maxMLBatchTaskPerNode;
    private volatile String dispatchPolicy;
    private DiscoveryNodeHelper nodeHelper;

    public MLTaskDispatcher(ClusterService clusterService, Client client, Settings settings, DiscoveryNodeHelper nodeHelper) {
        this.clusterService = clusterService;
        this.client = client;
        this.nodeHelper = nodeHelper;
        this.maxMLBatchTaskPerNode = MLTaskManager.MAX_ML_TASK_PER_NODE;
        this.nextNode = new AtomicInteger(0);
        this.dispatchPolicy = ML_COMMONS_TASK_DISPATCH_POLICY.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_TASK_DISPATCH_POLICY, it -> dispatchPolicy = it);
    }

    /**
     * Dispatch task to target node.
     * @param actionListener action listener
     */
    public void dispatch(ActionListener<String> actionListener) {
        if ("round_robin".equals(dispatchPolicy)) {
            dispatchTaskWithRoundRobin(actionListener);
        } else if ("least_load".equals(dispatchPolicy)) {
            dispatchTaskWithLeastLoad(actionListener);
        } else {
            throw new IllegalArgumentException("Unknown policy");
        }
    }

    public void dispatchModel(String[] nodeIds, ActionListener<String> actionListener) {
        if (nodeIds == null || nodeIds.length == 0) {
            throw new IllegalArgumentException("Model not loaded yet");
        }
        if ("round_robin".equals(dispatchPolicy)) {
            dispatchTaskWithRoundRobin(nodeIds, actionListener);
        } else if ("least_load".equals(dispatchPolicy)) {
            dispatchTaskWithLeastLoad(nodeIds, actionListener);
        } else {
            throw new IllegalArgumentException("Unknown policy");
        }
    }

    private void dispatchTaskWithRoundRobin(String[] mlNodes, ActionListener<String> listener) {
        int currentNode = nextNode.getAndIncrement();
        if (currentNode > mlNodes.length - 1) {
            currentNode = 0;
            nextNode.set(currentNode + 1);
        }
        listener.onResponse(mlNodes[currentNode]);
    }

    private void dispatchTaskWithLeastLoad(String[] nodeIds, ActionListener<String> listener) {
        MLStatsNodesRequest MLStatsNodesRequest = new MLStatsNodesRequest(nodeHelper.getNodes(nodeIds));
        MLStatsNodesRequest
            .addNodeLevelStats(ImmutableSet.of(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT, MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE));

        client.execute(MLStatsNodesAction.INSTANCE, MLStatsNodesRequest, ActionListener.wrap(mlStatsResponse -> {
            // Check JVM pressure
            List<MLStatsNodeResponse> candidateNodeResponse = mlStatsResponse
                .getNodes()
                .stream()
                .filter(stat -> (long) stat.getNodeLevelStat(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE) < DEFAULT_JVM_HEAP_USAGE_THRESHOLD)
                .collect(Collectors.toList());

            if (candidateNodeResponse.size() == 0) {
                String errorMessage = "All nodes' memory usage exceeds limitation "
                    + DEFAULT_JVM_HEAP_USAGE_THRESHOLD
                    + ". No eligible node available to run ml jobs ";
                log.warn(errorMessage);
                listener.onFailure(new LimitExceededException(errorMessage));
                return;
            }

            // Check # of executing ML task
            candidateNodeResponse = candidateNodeResponse
                .stream()
                .filter(stat -> (Long) stat.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT) < maxMLBatchTaskPerNode)
                .collect(Collectors.toList());
            if (candidateNodeResponse.size() == 0) {
                String errorMessage = "All nodes' executing ML task count reach limitation.";
                log.warn(errorMessage);
                listener.onFailure(new LimitExceededException(errorMessage));
                return;
            }

            // sort nodes by JVM usage percentage and # of executing ML task
            Optional<MLStatsNodeResponse> targetNode = candidateNodeResponse
                .stream()
                .sorted((MLStatsNodeResponse r1, MLStatsNodeResponse r2) -> {
                    int result = ((Long) r1.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT))
                        .compareTo((Long) r2.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT));
                    if (result == 0) {
                        // if multiple nodes have same running task count, choose the one with least
                        // JVM heap usage.
                        return ((Long) r1.getNodeLevelStat(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE))
                            .compareTo((Long) r2.getNodeLevelStat(MLNodeLevelStat.ML_NODE_JVM_HEAP_USAGE));
                    }
                    return result;
                })
                .findFirst();
            listener.onResponse(targetNode.get().getNode().getId());
        }, exception -> {
            log.error("Failed to get node's task stats", exception);
            listener.onFailure(exception);
        }));
    }

    private void dispatchTaskWithLeastLoad(ActionListener<String> listener) {
        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes();
        dispatchTaskWithLeastLoad(nodeHelper.getNodeIds(eligibleNodes), listener);
    }

    private void dispatchTaskWithRoundRobin(ActionListener<String> listener) {
        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes();
        dispatchTaskWithRoundRobin(nodeHelper.getNodeIds(eligibleNodes), listener);
    }

}
