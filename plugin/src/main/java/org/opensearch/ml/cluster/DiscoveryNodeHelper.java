/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import lombok.extern.log4j.Log4j2;

import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.utils.MLNodeUtils;

/**
 * Util class to filter nodes
 */
@Log4j2
public class DiscoveryNodeHelper {
    private final ClusterService clusterService;
    private final HotDataNodePredicate eligibleNodeFilter;

    public DiscoveryNodeHelper(ClusterService clusterService) {
        this.clusterService = clusterService;
        eligibleNodeFilter = new HotDataNodePredicate();
    }

    public String[] getEligibleNodeIds() {
        DiscoveryNode[] nodes = getEligibleNodes();
        String[] nodeIds = new String[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodeIds[i] = nodes[i].getId();
        }
        return nodeIds;
    }

    public DiscoveryNode[] getEligibleNodes() {
        ClusterState state = this.clusterService.state();
        final List<DiscoveryNode> eligibleMLNodes = new ArrayList<>();
        final List<DiscoveryNode> eligibleDataNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            if (MLNodeUtils.isMLNode(node)) {
                eligibleMLNodes.add(node);
            }
            if (node.isDataNode() && isEligibleDataNode(node)) {
                eligibleDataNodes.add(node);
            }
        }
        if (eligibleMLNodes.size() > 0) {
            DiscoveryNode[] mlNodes = eligibleMLNodes.toArray(new DiscoveryNode[0]);
            log.debug("Find {} dedicated ML nodes: {}", eligibleMLNodes.size(), Arrays.toString(mlNodes));
            return mlNodes;
        } else {
            DiscoveryNode[] dataNodes = eligibleDataNodes.toArray(new DiscoveryNode[0]);
            log.debug("Find no dedicated ML nodes. But have {} data nodes: {}", eligibleDataNodes.size(), Arrays.toString(dataNodes));
            return dataNodes;
        }
    }

    public DiscoveryNode[] getAllNodes() {
        ClusterState state = this.clusterService.state();
        final List<DiscoveryNode> nodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            nodes.add(node);
        }
        return nodes.toArray(new DiscoveryNode[0]);
    }

    public String[] getAllNodeIds() {
        ClusterState state = this.clusterService.state();
        final List<String> allNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            allNodes.add(node.getId());
        }
        return allNodes.toArray(new String[0]);
    }

    public DiscoveryNode[] getNodes(String[] nodeIds) {
        ClusterState state = this.clusterService.state();
        Set<String> nodes = new HashSet<>();
        for (String nodeId : nodeIds) {
            nodes.add(nodeId);
        }
        List<DiscoveryNode> discoveryNodes = new ArrayList<>();
        for (DiscoveryNode node : state.nodes()) {
            if (nodes.contains(node.getId())) {
                discoveryNodes.add(node);
            }
        }
        return discoveryNodes.toArray(new DiscoveryNode[0]);
    }

    public String[] getNodeIds(DiscoveryNode[] nodes) {
        List<String> nodeIds = new ArrayList<>();
        for (DiscoveryNode node : nodes) {
            nodeIds.add(node.getId());
        }
        return nodeIds.toArray(new String[0]);
    }

    public boolean isEligibleDataNode(DiscoveryNode node) {
        return eligibleNodeFilter.test(node);
    }

    public DiscoveryNode getNode(String nodeId) {
        ClusterState state = this.clusterService.state();
        for (DiscoveryNode node : state.nodes()) {
            if (node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    static class HotDataNodePredicate implements Predicate<DiscoveryNode> {
        @Override
        public boolean test(DiscoveryNode discoveryNode) {
            return discoveryNode.isDataNode()
                && discoveryNode
                    .getAttributes()
                    .getOrDefault(CommonValue.BOX_TYPE_KEY, CommonValue.HOT_BOX_TYPE)
                    .equals(CommonValue.HOT_BOX_TYPE);
        }
    }
}
