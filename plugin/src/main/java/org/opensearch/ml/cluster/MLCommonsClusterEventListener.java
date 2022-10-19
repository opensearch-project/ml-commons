/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.log4j.Log4j2;

import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;

@Log4j2
public class MLCommonsClusterEventListener implements ClusterStateListener {

    private final ClusterService clusterService;
    private final MLModelManager mlModelManager;
    private final MLTaskManager mlTaskManager;

    public MLCommonsClusterEventListener(ClusterService clusterService, MLModelManager mlModelManager, MLTaskManager mlTaskManager) {
        this.clusterService = clusterService;
        this.clusterService.addListener(this);
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        DiscoveryNodes.Delta delta = event.nodesDelta();
        if (delta.removed()) {
            Set<String> removedNodeIds = delta.removedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toSet());
            mlModelManager.removeWorkerNodes(removedNodeIds);
        }
    }
}
