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
import org.opensearch.gateway.GatewayService;
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

        // Apply live changes to the retention job interval (dynamic PUT _cluster/settings) by upserting the persisted
        // job document. The version bump is observed by JobScheduler's JobSweeper, which reschedules with no restart.
        // The consumer runs on every node, so gate the write on the elected cluster manager to avoid multi-node churn.
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_INTERVAL_HOURS, newIntervalHours -> {
                if (shouldManageMemoryRetentionJob()) {
                    mlTaskManager.upsertMemoryRetentionJob(newIntervalHours);
                }
            });
    }

    /**
     * Single-writer gate for memory-retention job writes that mutate the shared, fixed-id job document. Exactly one
     * node (the elected cluster manager) should ever upsert/reconcile, and only when agentic memory AND memory
     * retention are enabled (mirroring the startup scheduling path exactly). The {@link #jobsIndexReadyForWrite()}
     * check mirrors the startup loop's rolling-upgrade guard so a live setting change during a mixed-version upgrade
     * does not create the new {@code .plugins-ml-jobs} index before the cluster is ready for it. The retention job
     * schedules and runs under multi-tenancy too: it is a single cluster-wide, system-context janitor that cleans
     * every container across every tenant, with per-container memory_container_id filters providing tenant isolation.
     * See RFC #4859.
     */
    private boolean shouldManageMemoryRetentionJob() {
        return clusterService.state().nodes().isLocalNodeElectedClusterManager()
            && jobsIndexReadyForWrite()
            && mlFeatureEnabledSetting.isAgenticMemoryEnabled()
            && mlFeatureEnabledSetting.isMemoryRetentionEnabled();
    }

    /**
     * Rolling-upgrade guard shared by the startup scheduling path and the live interval-change consumer. It is safe to
     * write the {@code .plugins-ml-jobs} job document only when:
     *   - the index already exists (writing a document to an existing index cannot strand a replica), or
     *   - every node in the cluster is on at least this node's version, so a newly created index's replicas are
     *     allocatable anywhere.
     * While a rolling upgrade is in flight neither holds, so the write is deferred rather than creating the index
     * with stranded replicas (which leaves the cluster yellow until enough nodes are upgraded).
     */
    private boolean jobsIndexReadyForWrite() {
        ClusterState state = clusterService.state();
        return state.getMetadata().hasIndex(ML_JOBS_INDEX) || state.nodes().getMinNodeVersion().onOrAfter(Version.CURRENT);
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

        // The job-starting logic below reads and writes the .plugins-ml-jobs index. On a cluster restart this listener
        // fires before the state is usable, so those reads/writes would fail and leave the one-shot
        // startedMemoryRetentionJob / startedStatsJob flags stuck true with no retry until the next restart. Defer until
        // the state is ready; clusterChanged fires again as startup progresses, with the flags still unset. Two gates:
        // 1. cluster state not yet recovered -> a GET/index hits a "state not recovered" global block.
        // 2. the jobs index exists (restart) but its primary shard is not yet allocated -> a GET hits
        // NoShardAvailableActionException. (On a fresh cluster the index does not exist yet; the CREATE path seeds
        // it, so we only wait when it already exists.)
        if (state.blocks().hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)) {
            return;
        }
        if (state.getMetadata().hasIndex(ML_JOBS_INDEX)
            && (state.routingTable().index(ML_JOBS_INDEX) == null || !state.routingTable().index(ML_JOBS_INDEX).allPrimaryShardsActive())) {
            return;
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
        if (jobsIndexReadyForWrite()) {
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
                && !this.startedMemoryRetentionJob) {
                // Read the effective interval, honoring OpenSearch precedence (transient > persistent > opensearch.yml
                // > default). clusterService.getSettings() holds only node bootstrap settings (opensearch.yml / CLI),
                // frozen at construction; state.getMetadata().settings() holds the persistent/transient values set via
                // PUT _cluster/settings. Overlaying metadata on the node settings lets dynamic cluster settings win
                // while still honoring an opensearch.yml value. Reading only one source would drop the other and make
                // reconcile (which actively upserts on mismatch) revert an operator's chosen interval on restart.
                Settings effectiveSettings = Settings.builder().put(clusterService.getSettings()).put(settings).build();
                int intervalHours = MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_INTERVAL_HOURS.get(effectiveSettings);
                // CREATE (conflict-swallowing) seeds the job doc; safe to run on any node.
                mlTaskManager.indexMemoryRetentionJob(intervalHours);
                // Reconcile a settings.yml / restart value onto the already-existing (write-once) doc. This upserts
                // only when the persisted interval differs, so restart doesn't keep resetting the next-run anchor.
                // Gate on the elected cluster manager so exactly one node ever writes the shared doc.
                if (clusterService.state().nodes().isLocalNodeElectedClusterManager()) {
                    mlTaskManager.reconcileMemoryRetentionJob(intervalHours);
                }
                this.startedMemoryRetentionJob = true;
            }
        }
    }
}
