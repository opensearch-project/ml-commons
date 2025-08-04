/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_EXCLUDE_NODE_NAMES;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_ONLY_RUN_ON_ML_NODE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.Strings;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.utils.MLNodeUtils;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DiscoveryNodeHelper {
    private final ClusterService clusterService;
    private final HotDataNodePredicate eligibleNodeFilter;
    private volatile Boolean onlyRunOnMLNode;
    private volatile Set<String> excludedNodeNames;
    private volatile Set<String> remoteModelEligibleNodeRoles;
    private volatile Set<String> localModelEligibleNodeRoles;

    private static final Set<FunctionName> NON_LOCAL_FUNCTIONS = Set.of(FunctionName.REMOTE, FunctionName.AGENT, FunctionName.TOOL);

    public DiscoveryNodeHelper(ClusterService clusterService, Settings settings) {
        this.clusterService = clusterService;
        eligibleNodeFilter = new HotDataNodePredicate();
        onlyRunOnMLNode = ML_COMMONS_ONLY_RUN_ON_ML_NODE.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_ONLY_RUN_ON_ML_NODE, it -> onlyRunOnMLNode = it);
        excludedNodeNames = Strings.commaDelimitedListToSet(ML_COMMONS_EXCLUDE_NODE_NAMES.get(settings));
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_EXCLUDE_NODE_NAMES, it -> excludedNodeNames = Strings.commaDelimitedListToSet(it));
        remoteModelEligibleNodeRoles = new HashSet<>();
        remoteModelEligibleNodeRoles.addAll(ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES.get(settings));
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES, it -> {
            remoteModelEligibleNodeRoles = new HashSet<>(it);
        });
        localModelEligibleNodeRoles = new HashSet<>();
        localModelEligibleNodeRoles.addAll(ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES.get(settings));
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES, it -> {
            localModelEligibleNodeRoles = new HashSet<>(it);
        });
    }

    public String[] getEligibleNodeIds(FunctionName functionName) {
        DiscoveryNode[] nodes = getEligibleNodes(functionName);
        String[] nodeIds = new String[nodes.length];
        for (int i = 0; i < nodes.length; i++) {
            nodeIds[i] = nodes[i].getId();
        }
        return nodeIds;
    }

    public DiscoveryNode[] getEligibleNodes(FunctionName functionName) {
        ClusterState state = this.clusterService.state();
        final Set<DiscoveryNode> eligibleNodes = new HashSet<>();
        for (DiscoveryNode node : state.nodes()) {
            if (excludedNodeNames != null && excludedNodeNames.contains(node.getName())) {
                continue;
            }
            if (NON_LOCAL_FUNCTIONS.contains(functionName)) {
                getEligibleNode(remoteModelEligibleNodeRoles, eligibleNodes, node);
            } else { // local model
                if (onlyRunOnMLNode) {
                    if (MLNodeUtils.isMLNode(node)) {
                        eligibleNodes.add(node);
                    }
                } else {
                    getEligibleNode(localModelEligibleNodeRoles, eligibleNodes, node);
                }
            }
        }
        return eligibleNodes.toArray(new DiscoveryNode[0]);
    }

    private void getEligibleNode(Set<String> allowedNodeRoles, Set<DiscoveryNode> eligibleNodes, DiscoveryNode node) {
        if (allowedNodeRoles.contains("data") && isEligibleDataNode(node)) {
            eligibleNodes.add(node);
        }
        for (String nodeRole : allowedNodeRoles) {
            if (!"data".equals(nodeRole) && node.getRoles().stream().anyMatch(r -> r.roleName().equals(nodeRole))) {
                eligibleNodes.add(node);
            }
        }
    }

    public String[] filterEligibleNodes(FunctionName functionName, String[] nodeIds) {
        if (nodeIds == null || nodeIds.length == 0) {
            return nodeIds;
        }
        DiscoveryNode[] nodes = getNodes(nodeIds);
        final Set<String> eligibleNodes = new HashSet<>();
        for (DiscoveryNode node : nodes) {
            if (excludedNodeNames != null && excludedNodeNames.contains(node.getName())) {
                continue;
            }
            if (functionName == FunctionName.REMOTE) {// remote model
                getEligibleNodeIds(remoteModelEligibleNodeRoles, eligibleNodes, node);
            } else { // local model
                if (onlyRunOnMLNode) {
                    if (MLNodeUtils.isMLNode(node)) {
                        eligibleNodes.add(node.getId());
                    }
                } else {
                    getEligibleNodeIds(localModelEligibleNodeRoles, eligibleNodes, node);
                }
            }
        }
        return eligibleNodes.toArray(new String[0]);
    }

    private void getEligibleNodeIds(Set<String> allowedNodeRoles, Set<String> eligibleNodes, DiscoveryNode node) {
        if (allowedNodeRoles.contains("data") && isEligibleDataNode(node)) {
            eligibleNodes.add(node.getId());
        }
        for (String nodeRole : allowedNodeRoles) {
            if (!"data".equals(nodeRole) && node.getRoles().stream().anyMatch(r -> r.roleName().equals(nodeRole))) {
                eligibleNodes.add(node.getId());
            }
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
