/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_MAX_ML_TASK_PER_NODE;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TASK_DISPATCH_POLICY;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.naming.LimitExceededException;

import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.action.stats.MLStatsNodeResponse;
import org.opensearch.ml.action.stats.MLStatsNodesAction;
import org.opensearch.ml.action.stats.MLStatsNodesRequest;
import org.opensearch.ml.cluster.DiscoveryNodeHelper;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.MLNodeLevelStat;

import lombok.extern.log4j.Log4j2;

/**
 * MLTaskDispatcher is responsible for dispatching the ml tasks.
 * TODO: Add more test
 */
@Log4j2
public class MLTaskDispatcher {
    // todo: move to a config class
    private final short DEFAULT_JVM_HEAP_USAGE_THRESHOLD = 85;
    private final String ROUND_ROBIN = "round_robin";
    private final String LEAST_LOAD = "least_load";
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
        this.maxMLBatchTaskPerNode = ML_COMMONS_MAX_ML_TASK_PER_NODE.get(settings);
        this.nextNode = new AtomicInteger(0);
        this.dispatchPolicy = ML_COMMONS_TASK_DISPATCH_POLICY.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_TASK_DISPATCH_POLICY, it -> dispatchPolicy = it);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_MAX_ML_TASK_PER_NODE, it -> maxMLBatchTaskPerNode = it);
    }

    /**
     * Dispatch task to target node.
     * @param functionName function name
     * @param actionListener action listener
     */
    public void dispatch(FunctionName functionName, ActionListener<DiscoveryNode> actionListener) {
        if (ROUND_ROBIN.equals(dispatchPolicy)) {
            dispatchTaskWithRoundRobin(functionName, actionListener);
        } else if (LEAST_LOAD.equals(dispatchPolicy)) {
            dispatchTaskWithLeastLoad(functionName, actionListener);
        } else {
            throw new IllegalArgumentException("Unknown policy");
        }
    }

    public void dispatchPredictTask(String[] nodeIds, ActionListener<DiscoveryNode> actionListener) {
        if (nodeIds == null || nodeIds.length == 0) {
            throw new IllegalArgumentException("no eligible node to run predict request");
        }
        if (ROUND_ROBIN.equals(dispatchPolicy)) {
            dispatchTaskWithRoundRobin(
                nodeIds,
                ActionListener.wrap(nodeId -> actionListener.onResponse(nodeHelper.getNode(nodeId)), e -> actionListener.onFailure(e))
            );
        } else if (LEAST_LOAD.equals(dispatchPolicy)) {
            dispatchTaskWithLeastLoad(nodeIds, actionListener);
        } else {
            throw new IllegalArgumentException("Unknown policy");
        }
    }

    private <T> void dispatchTaskWithRoundRobin(T[] nodes, ActionListener<T> listener) {
        int currentNode = nextNode.getAndIncrement();
        if (currentNode > nodes.length - 1) {
            currentNode = 0;
            nextNode.set(currentNode + 1);
        }
        listener.onResponse(nodes[currentNode]);
    }

    private void dispatchTaskWithLeastLoad(String[] nodeIds, ActionListener<DiscoveryNode> listener) {
        DiscoveryNode[] nodes = nodeHelper.getNodes(nodeIds);
        dispatchTaskWithLeastLoad(nodes, listener);
    }

    private void dispatchTaskWithLeastLoad(DiscoveryNode[] nodes, ActionListener<DiscoveryNode> listener) {
        MLStatsNodesRequest MLStatsNodesRequest = new MLStatsNodesRequest(nodes);
        MLStatsNodesRequest.addNodeLevelStats(Set.of(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT, MLNodeLevelStat.ML_JVM_HEAP_USAGE));

        client.execute(MLStatsNodesAction.INSTANCE, MLStatsNodesRequest, ActionListener.wrap(mlStatsResponse -> {
            // Check JVM pressure
            List<MLStatsNodeResponse> candidateNodeResponse = mlStatsResponse
                .getNodes()
                .stream()
                .filter(stat -> (long) stat.getNodeLevelStat(MLNodeLevelStat.ML_JVM_HEAP_USAGE) < DEFAULT_JVM_HEAP_USAGE_THRESHOLD)
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
                .filter(stat -> (Long) stat.getNodeLevelStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT) < maxMLBatchTaskPerNode)
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
                    int result = ((Long) r1.getNodeLevelStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT))
                        .compareTo((Long) r2.getNodeLevelStat(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));
                    if (result == 0) {
                        // if multiple nodes have same running task count, choose the one with least
                        // JVM heap usage.
                        return ((Long) r1.getNodeLevelStat(MLNodeLevelStat.ML_JVM_HEAP_USAGE))
                            .compareTo((Long) r2.getNodeLevelStat(MLNodeLevelStat.ML_JVM_HEAP_USAGE));
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

    private void dispatchTaskWithLeastLoad(FunctionName functionName, ActionListener<DiscoveryNode> listener) {
        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(functionName);
        dispatchTaskWithLeastLoad(eligibleNodes, listener);
    }

    private void dispatchTaskWithRoundRobin(FunctionName functionName, ActionListener<DiscoveryNode> listener) {
        DiscoveryNode[] eligibleNodes = nodeHelper.getEligibleNodes(functionName);
        if (eligibleNodes == null || eligibleNodes.length == 0) {
            throw new IllegalArgumentException(
                "No eligible node found to execute this request. It's best practice to"
                    + " provision ML nodes to serve your models. You can disable this setting to serve the model on your data"
                    + " node for development purposes by disabling the \"plugins.ml_commons.only_run_on_ml_node\" "
                    + "configuration using the _cluster/setting api"
            );
        }
        dispatchTaskWithRoundRobin(eligibleNodes, listener);
    }

}
