/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.CommonValue.ML_JOBS_INDEX;
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
import org.opensearch.ml.common.settings.MLCommonsSettings;
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
    private boolean startedStatsJob;
    private boolean startedMemoryRetentionJob;

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
            List<String> addedNodesIds = delta.addedNodes().stream().map(DiscoveryNode::getId).collect(Collectors.toList());
            mlModelAutoReDeployer.buildAutoReloadArrangement(addedNodesIds, state.getNodes().getClusterManagerNodeId());
        }

        /*
         * The stats collector and memory retention jobs live in the `.plugins-ml-jobs` index (introduced in 3.1,
         * replacing `.ml_commons_task_polling_job`). Indexing a job document auto-creates that index, and creating
         * an index while a rolling upgrade is in flight strands its replicas: the primary is allocated to the
         * new-version node that issued the write, and replicas can never be assigned to nodes older than the
         * primary's node, so the cluster stays yellow until enough nodes are upgraded.
         *
         * To avoid that, only touch the jobs index when it is safe:
         *   - the index already exists (writing a document to an existing index cannot strand a replica), or
         *   - every node in the cluster is on at least this node's version, so a newly created index's replicas
         *     are allocatable anywhere.
         * While a rolling upgrade is in flight neither holds; the jobs are deferred, not skipped — when the last
         * old node leaves, the resulting cluster state change re-runs this listener and the jobs are created then.
         */
        boolean jobsIndexExists = state.getMetadata().hasIndex(ML_JOBS_INDEX);
        boolean noOlderNodes = state.nodes().getMinNodeVersion().onOrAfter(Version.CURRENT);
        if (jobsIndexExists || noOlderNodes) {
            if (mlFeatureEnabledSetting.isMetricCollectionEnabled()
                && mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()
                && !jobsIndexExists
                && !this.startedStatsJob) {
                mlTaskManager.indexStatsCollectorJob(true);
                // using this variable in case if same node has a cluster state change event and the state is not updated yet
                this.startedStatsJob = true;
            }

            if (mlFeatureEnabledSetting.isAgenticMemoryEnabled()
                && mlFeatureEnabledSetting.isMemoryRetentionEnabled()
                && !MLCommonsSettings.ML_COMMONS_MULTI_TENANCY_ENABLED.get(clusterService.getSettings())
                && !this.startedMemoryRetentionJob) {
                int intervalHours = MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_INTERVAL_HOURS.get(clusterService.getSettings());
                mlTaskManager.indexMemoryRetentionJob(intervalHours);
                this.startedMemoryRetentionJob = true;
            }
        }
    }
}
