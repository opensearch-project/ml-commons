/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLCommonsClusterEventListener implements ClusterStateListener {

    private final ClusterService clusterService;
    private final MLModelManager mlModelManager;
    private final MLTaskManager mlTaskManager;
    private final MLModelCacheHelper modelCacheHelper;

    private final MLModelAutoReDeployer mlModelAutoReDeployer;

    public MLCommonsClusterEventListener(
        ClusterService clusterService,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        MLModelCacheHelper modelCacheHelper,
        MLModelAutoReDeployer mlModelAutoReDeployer
    ) {
        this.clusterService = clusterService;
        this.clusterService.addListener(this);
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.modelCacheHelper = modelCacheHelper;
        this.mlModelAutoReDeployer = mlModelAutoReDeployer;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        ClusterState previousState = event.previousState();
        ClusterState state = event.state();
        Settings previousSettings = previousState.getMetadata().settings();
        Settings settings = state.getMetadata().settings();
        long previousMonitoringReqCount = ML_COMMONS_MONITORING_REQUEST_COUNT.get(previousSettings);
        long monitoringReqCount = ML_COMMONS_MONITORING_REQUEST_COUNT.get(settings);
        if (previousMonitoringReqCount > monitoringReqCount) {
            modelCacheHelper.resizeMonitoringQueue(monitoringReqCount);
        }
        DiscoveryNodes.Delta delta = event.nodesDelta();
        if (delta.removed()) {
            Set<String> removedNodeIds = delta.removedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toSet());
            mlModelManager.removeWorkerNodes(removedNodeIds, false);
        } else if (delta.added()) {
            List<String> addedNodesIds = delta.addedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toList());
            mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodesIds, state.getNodes().getClusterManagerNodeId());
        }
    }
}
