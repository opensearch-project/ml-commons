/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_JOBS_INDEX;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterChangedEvent;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.block.ClusterBlocks;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.gateway.GatewayService;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

public class MLCommonsClusterEventListenerTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private MLModelManager mlModelManager;
    @Mock
    private MLTaskManager mlTaskManager;
    @Mock
    private MLModelCacheHelper modelCacheHelper;
    @Mock
    private MLModelAutoReDeployer mlModelAutoReDeployer;
    @Mock
    private Client client;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;
    @Mock
    private ClusterChangedEvent event;
    @Mock
    private ClusterState clusterState;
    @Mock
    private Metadata metadata;
    @Mock
    private ClusterBlocks clusterBlocks;
    @Mock
    private RoutingTable routingTable;

    private MLCommonsClusterEventListener listener;
    private ClusterSettings clusterSettings;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        clusterSettings = new ClusterSettings(
            Settings.EMPTY,
            new HashSet<>(Arrays.asList(MLCommonsSettings.ML_COMMONS_MEMORY_RETENTION_JOB_INTERVAL_HOURS))
        );
        when(clusterService.getClusterSettings()).thenReturn(clusterSettings);
        listener = new MLCommonsClusterEventListener(
            clusterService,
            mlModelManager,
            mlTaskManager,
            modelCacheHelper,
            mlModelAutoReDeployer,
            client,
            mlFeatureEnabledSetting
        );
    }

    public void testClusterChanged_AllNodesCurrent_MetricCollectionEnabled() {
        setupClusterState(false, createDataNode("n1", Version.CURRENT));

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexStatsCollectorJob(true);
    }

    public void testClusterChanged_MixedVersionCluster_NoIndex_NoJobsStarted() {
        // rolling upgrade in flight: one upgraded node, one old node, jobs index absent.
        // Creating the index now would strand its replica on version-allocation rules,
        // so neither job may be created.
        setupClusterState(false, createDataNode("new", Version.CURRENT), createDataNode("old", Version.V_3_1_0));

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MixedVersionCluster_IndexExists_RetentionJobStarted() {
        // writing a job document into an existing index cannot strand a replica,
        // so a mixed-version cluster must not block it
        setupClusterState(true, createDataNode("new", Version.CURRENT), createDataNode("old", Version.V_3_1_0));
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
    }

    public void testClusterChanged_JobsDeferredUntilUpgradeCompletes() {
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        // first event: upgrade in flight — deferred
        setupClusterState(false, createDataNode("new", Version.CURRENT), createDataNode("old", Version.V_3_1_0));
        listener.clusterChanged(event);
        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());

        // second event: last old node upgraded — job created
        setupClusterState(false, createDataNode("new", Version.CURRENT), createDataNode("old", Version.CURRENT));
        listener.clusterChanged(event);
        verify(mlTaskManager).indexMemoryRetentionJob(24);
    }

    public void testClusterChanged_IndexAlreadyPresent_StatsJobNotStarted() {
        setupClusterState(true, createDataNode("n1", Version.CURRENT));

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
    }

    public void testClusterChanged_MemoryRetentionJobStarted() {
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
    }

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenRetentionDisabled() {
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(false);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MemoryRetentionJobStarted_WhenMultiTenancyEnabled() {
        // RFC #4859: the retention job is a single cluster-wide, system-context janitor with per-container tenant
        // isolation, so it must be SCHEDULED even when multi-tenancy is enabled. Multi-tenancy no longer gates it.
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings())
            .thenReturn(org.opensearch.common.settings.Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build());

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
    }

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenRemoteMetadataStoreConfigured() {
        // RFC #4859: with a remote metadata store (e.g. AWS OpenSearch Serverless / DynamoDB), the container registry
        // is not in the local cluster, so the native-client retention job cannot enumerate it. It must NOT be scheduled.
        // Local-metadata multi-tenancy remains supported (see testClusterChanged_MemoryRetentionJobStarted_WhenMultiTenancyEnabled).
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings())
            .thenReturn(
                org.opensearch.common.settings.Settings
                    .builder()
                    .put("plugins.ml_commons.multi_tenancy_enabled", true)
                    .put("plugins.ml_commons.remote_metadata_type", "AWSOpenSearchService")
                    .build()
            );

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenAgenticMemoryDisabled() {
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MemoryRetentionJob_NonDefaultInterval() {
        // Healthy, fully-upgraded cluster (all nodes on CURRENT): the startup deferral guard (noOlderNodes) is
        // satisfied so the CREATE path runs. A pre-CURRENT node here would trip the rolling-upgrade deferral.
        setupClusterState(false, createDataNode("dataNode", Version.CURRENT));
        when(clusterService.getSettings())
            .thenReturn(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 1).build());

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(1);
    }

    public void testClusterChanged_MemoryRetentionReconcile_OnElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerState(dataNode, false, true);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
        verify(mlTaskManager).reconcileMemoryRetentionJob(24);
    }

    /**
     * Regression guard: an interval set dynamically via {@code PUT _cluster/settings} lives in cluster-state metadata,
     * NOT in the frozen node bootstrap settings returned by {@code clusterService.getSettings()}. The startup path must
     * read the effective value (metadata overlaid on node settings); otherwise reconcile would upsert the default and
     * silently revert the operator's persisted interval on restart.
     */
    public void testClusterChanged_MemoryRetentionReconcile_HonorsPersistedIntervalFromMetadata() {
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerState(dataNode, false, true);
        // Node bootstrap (opensearch.yml) has nothing; the interval was set via `PUT _cluster/settings persistent:`,
        // which lands in metadata.persistentSettings(). Fix #1 overlays those on top of node settings so dynamic
        // values win — this asserts the reconcile reads the persisted value (1), not the default (24).
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(metadata.persistentSettings())
            .thenReturn(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 1).build());
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(1);
        verify(mlTaskManager).reconcileMemoryRetentionJob(1);
    }

    public void testClusterChanged_MemoryRetentionReconcile_SkippedWhenNotElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerState(dataNode, false, false);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        // The conflict-safe CREATE still runs on any node, but reconcile (which upserts) must not.
        verify(mlTaskManager).indexMemoryRetentionJob(24);
        verify(mlTaskManager, never()).reconcileMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_SkippedWhenStateNotRecovered() {
        // On a restart this listener fires before cluster state is recovered; the startup block must be deferred so a
        // read/write against .plugins-ml-jobs does not hit the state-not-recovered block and wedge the one-shot flags.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.V_3_1_0);
        setupElectedClusterManagerState(dataNode, true, true);
        when(clusterBlocks.hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)).thenReturn(true);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
        verify(mlTaskManager, never()).reconcileMemoryRetentionJob(anyInt());
        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
    }

    public void testClusterChanged_SkippedWhenJobsIndexPrimaryShardsNotActive() {
        // State is recovered, but on a restart the existing jobs index's primary shard may not be allocated yet; a GET
        // would throw NoShardAvailableActionException. The startup block must defer until the shards are active.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.V_3_1_0);
        setupElectedClusterManagerState(dataNode, true, true);
        IndexRoutingTable indexRoutingTable = mock(IndexRoutingTable.class);
        when(routingTable.index(ML_JOBS_INDEX)).thenReturn(indexRoutingTable);
        when(indexRoutingTable.allPrimaryShardsActive()).thenReturn(false);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
        verify(mlTaskManager, never()).reconcileMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_ProceedsWhenJobsIndexAbsent() {
        // A fresh cluster has no jobs index yet; the shard-active gate must be skipped so the CREATE path can seed it.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerState(dataNode, false, true);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
        verify(mlTaskManager).reconcileMemoryRetentionJob(24);
    }

    public void testSettingsUpdateConsumer_UpsertsOnElectedClusterManager() {
        // All nodes on CURRENT (no mixed-version upgrade in flight), elected cluster manager, agentic memory AND
        // retention enabled, multi-tenancy off -> the live interval change upserts the persisted job document.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager).upsertMemoryRetentionJob(2);
    }

    public void testSettingsUpdateConsumer_SkippedWhenNotElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode("dataNode", Version.V_3_1_0);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode).localNodeId(dataNode.getId()).build();
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterService.state()).thenReturn(clusterState);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_SkippedWhenAgenticMemoryDisabled() {
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_UpsertsWhenMultiTenancyEnabled() {
        // RFC #4859: multi-tenancy no longer gates the retention job, so a live interval change on the elected
        // cluster manager must still upsert the shared job doc even when multi-tenancy is enabled. All nodes on
        // CURRENT so the rolling-upgrade guard (jobsIndexReadyForWrite) passes and multi-tenancy is the only variable.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build());
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager).upsertMemoryRetentionJob(2);
    }

    public void testSettingsUpdateConsumer_SkippedWhenMixedVersionCluster() {
        // Elected cluster manager, agentic memory AND retention on, multi-tenancy off, jobs index absent, but a data
        // node OLDER than current is present (mixed-version rolling upgrade). jobsIndexReadyForWrite() must be false so
        // the consumer does not create the new jobs index yet, mirroring the startup path's rolling-upgrade guard.
        // Retention is explicitly enabled so the version guard (not the feature flag) is what blocks the upsert.
        DiscoveryNode olderDataNode = createDataNode("dataNode", Version.V_3_0_0);
        setupElectedClusterManagerConsumerState(olderDataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_SkippedWhenRetentionDisabled() {
        // Elected cluster manager, all nodes on CURRENT, agentic memory on, multi-tenancy off, but memory retention is
        // DISABLED. The consumer gates on isMemoryRetentionEnabled() (matching the startup path), so no upsert fires.
        DiscoveryNode dataNode = createDataNode("dataNode", Version.CURRENT);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isMemoryRetentionEnabled()).thenReturn(false);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    private void setupElectedClusterManagerConsumerState(DiscoveryNode node) {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node).localNodeId(node.getId()).clusterManagerNodeId(node.getId()).build();
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterService.state()).thenReturn(clusterState);
        // shouldManageMemoryRetentionJob() -> jobsIndexReadyForWrite() reads getMetadata().hasIndex(ML_JOBS_INDEX);
        // stub it so the version-based rolling-upgrade guard governs (index absent -> falls back to minNodeVersion check).
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(metadata.hasIndex(ML_JOBS_INDEX)).thenReturn(false);
    }

    private DiscoveryNode createDataNode(String id, Version version) {
        return new DiscoveryNode(
            id,
            id + "Id",
            buildNewFakeTransportAddress(),
            Collections.emptyMap(),
            Collections.singleton(DiscoveryNodeRole.DATA_ROLE),
            version
        );
    }

    private void setupClusterState(boolean hasMLJobsIndex, DiscoveryNode... discoveryNodes) {
        DiscoveryNodes.Builder builder = DiscoveryNodes.builder();
        for (DiscoveryNode node : discoveryNodes) {
            builder.add(node);
        }
        DiscoveryNodes nodes = builder.build();

        when(event.state()).thenReturn(clusterState);
        when(event.previousState()).thenReturn(clusterState);
        when(event.nodesDelta()).thenReturn(mock(DiscoveryNodes.Delta.class));
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);
        when(metadata.hasIndex(ML_JOBS_INDEX)).thenReturn(hasMLJobsIndex);
        when(metadata.settings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        when(metadata.persistentSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        when(metadata.transientSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        setupStateReady();
    }

    private void setupElectedClusterManagerState(DiscoveryNode node, boolean hasMLJobsIndex, boolean localNodeIsClusterManager) {
        DiscoveryNodes.Builder nodesBuilder = DiscoveryNodes.builder().add(node).localNodeId(node.getId());
        if (localNodeIsClusterManager) {
            nodesBuilder.clusterManagerNodeId(node.getId());
        }
        DiscoveryNodes nodes = nodesBuilder.build();

        when(event.state()).thenReturn(clusterState);
        when(event.previousState()).thenReturn(clusterState);
        when(event.nodesDelta()).thenReturn(mock(DiscoveryNodes.Delta.class));
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);
        when(metadata.hasIndex(ML_JOBS_INDEX)).thenReturn(hasMLJobsIndex);
        when(metadata.settings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        when(metadata.persistentSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        when(metadata.transientSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
        setupStateReady();
    }

    /**
     * Default "state is ready" stubs for the startup guards: no state-not-recovered block, and (if the jobs index is
     * present) its primary shards are active. Individual tests override these to exercise the deferral paths.
     */
    private void setupStateReady() {
        when(clusterState.blocks()).thenReturn(clusterBlocks);
        when(clusterBlocks.hasGlobalBlock(GatewayService.STATE_NOT_RECOVERED_BLOCK)).thenReturn(false);
        when(clusterState.routingTable()).thenReturn(routingTable);
        IndexRoutingTable indexRoutingTable = mock(IndexRoutingTable.class);
        when(routingTable.index(ML_JOBS_INDEX)).thenReturn(indexRoutingTable);
        when(indexRoutingTable.allPrimaryShardsActive()).thenReturn(true);
    }
}
