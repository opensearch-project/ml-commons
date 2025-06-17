/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.CommonValue.TASK_POLLING_JOB_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MONITORING_REQUEST_COUNT;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.opensearch.Version;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.ClusterStateListener;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLCommonsClusterEventListener implements ClusterStateListener {

    private final ClusterService clusterService;
    private final MLModelManager mlModelManager;
    private final MLTaskManager mlTaskManager;
    private final MLModelCacheHelper modelCacheHelper;
    private final MLModelAutoReDeployer mlModelAutoReDeployer;
    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public MLCommonsClusterEventListener(
        ClusterService clusterService,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        MLModelCacheHelper modelCacheHelper,
        MLModelAutoReDeployer mlModelAutoReDeployer,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.clusterService = clusterService;
        this.clusterService.addListener(this);
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.modelCacheHelper = modelCacheHelper;
        this.mlModelAutoReDeployer = mlModelAutoReDeployer;
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
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
            for (DiscoveryNode node : delta.addedNodes()) {
                // 3.1 introduces a new index for the job scheduler to track jobs
                // the statsCollectorJob needs to be run when a cluster is started with the stats settings enabled
                // As a result, we need to wait for a data node to come up before creating the new jobs index
                if (node.isDataNode() && Version.V_3_1_0.onOrAfter(node.getVersion())) {
                    if (mlFeatureEnabledSetting.isMetricCollectionEnabled() && mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()) {
                        mlTaskManager.startStatsCollectorJob();
                    }

                    if (clusterService.state().getMetadata().hasIndex(TASK_POLLING_JOB_INDEX)) {
                        mlTaskManager.startTaskPollingJob();
                    }
                }
            }

            List<String> addedNodesIds = delta.addedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toList());
            mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodesIds, state.getNodes().getClusterManagerNodeId());
        }
    }
}
