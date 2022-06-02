/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.naming.LimitExceededException;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.client.Client;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.utils.MLNodeUtils;

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
    private volatile Integer maxMLBatchTaskPerNode;

    public MLTaskDispatcher(ClusterService clusterService, Client client) {
        this.clusterService = clusterService;
        this.client = client;
        this.maxMLBatchTaskPerNode = MLTaskManager.MAX_ML_TASK_PER_NODE;
    }

    /**
     * Select least loaded node based on ML_EXECUTING_TASK_COUNT and JVM_HEAP_USAGE
     * @param listener Action listener
     */
    public void dispatchTask(ActionListener<DiscoveryNode> listener) {
        // todo: add ML node type setting check
        // DiscoveryNode[] mlNodes = getEligibleMLNodes();
        DiscoveryNode[] mlNodes = getEligibleDataNodes();
        MLStatsNodesRequest MLStatsNodesRequest = new MLStatsNodesRequest(mlNodes);
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
            listener.onResponse(targetNode.get().getNode());
        }, exception -> {
            log.error("Failed to get node's task stats", exception);
            listener.onFailure(exception);
        }));
    }

    private DiscoveryNode[] getEligibleMLNodes() {
        ClusterState state = this.clusterService.state();
        final List<DiscoveryNode> eligibleNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            if (MLNodeUtils.isMLNode(node)) {
                eligibleNodes.add(node);
            }
        }
        return eligibleNodes.toArray(new DiscoveryNode[0]);
    }

    private DiscoveryNode[] getEligibleDataNodes() {
        ClusterState state = this.clusterService.state();
        final List<DiscoveryNode> eligibleDataNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            if (node.isDataNode()) {
                eligibleDataNodes.add(node);
            }
        }
        return eligibleDataNodes.toArray(new DiscoveryNode[0]);
    }
}
