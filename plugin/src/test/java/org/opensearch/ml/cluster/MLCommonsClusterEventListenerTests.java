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
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
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

    public void testClusterChanged_WithV31DataNode_MetricCollectionEnabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexStatsCollectorJob(true);
    }

    public void testClusterChanged_WithPreV31DataNode_NoJobsStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_0_0);
        setupClusterState(dataNode, false);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
    }

    public void testClusterChanged_WithPostV31DataNode_JobsStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_2_0);
        setupClusterState(dataNode, false);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexStatsCollectorJob(true);
    }

    public void testClusterChanged_IndexAlreadyPresent_JobNotStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, true);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
    }

    public void testClusterChanged_MemoryRetentionJobStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
    }

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenMultiTenancyEnabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);
        when(clusterService.getSettings())
            .thenReturn(org.opensearch.common.settings.Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build());

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenAgenticMemoryDisabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);
        when(clusterService.getSettings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexMemoryRetentionJob(anyInt());
    }

    public void testClusterChanged_MemoryRetentionJob_NonDefaultInterval() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);
        when(clusterService.getSettings())
            .thenReturn(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 1).build());

        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(1);
    }

    public void testClusterChanged_MemoryRetentionReconcile_OnElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupElectedClusterManagerState(dataNode, false, true);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexMemoryRetentionJob(24);
        verify(mlTaskManager).reconcileMemoryRetentionJob(24);
    }

    public void testClusterChanged_MemoryRetentionReconcile_SkippedWhenNotElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupElectedClusterManagerState(dataNode, false, false);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        // The conflict-safe CREATE still runs on any node, but reconcile (which upserts) must not.
        verify(mlTaskManager).indexMemoryRetentionJob(24);
        verify(mlTaskManager, never()).reconcileMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_UpsertsOnElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        DiscoveryNodes nodes = DiscoveryNodes
            .builder()
            .add(dataNode)
            .localNodeId(dataNode.getId())
            .clusterManagerNodeId(dataNode.getId())
            .build();
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterService.state()).thenReturn(clusterState);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager).upsertMemoryRetentionJob(2);
    }

    public void testSettingsUpdateConsumer_SkippedWhenNotElectedClusterManager() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(dataNode).localNodeId(dataNode.getId()).build();
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterService.state()).thenReturn(clusterState);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_SkippedWhenAgenticMemoryDisabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(false);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_SkippedWhenMultiTenancyEnabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupElectedClusterManagerConsumerState(dataNode);
        when(clusterService.getSettings()).thenReturn(Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build());
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    public void testSettingsUpdateConsumer_SkippedWhenNoDataNodeOnOrAfterV31() {
        // Elected cluster manager, agentic memory on, multi-tenancy off, but the only data node is pre-3.1 (mixed-version
        // rolling upgrade). The consumer must not write the new jobs index yet, mirroring the startup path's guard.
        DiscoveryNode preV31DataNode = createDataNode(Version.V_3_0_0);
        setupElectedClusterManagerConsumerState(preV31DataNode);
        when(clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(mlFeatureEnabledSetting.isAgenticMemoryEnabled()).thenReturn(true);

        clusterSettings.applySettings(Settings.builder().put("plugins.ml_commons.memory.retention_job_interval_hours", 2).build());

        verify(mlTaskManager, never()).upsertMemoryRetentionJob(anyInt());
    }

    private void setupElectedClusterManagerConsumerState(DiscoveryNode node) {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node).localNodeId(node.getId()).clusterManagerNodeId(node.getId()).build();
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterService.state()).thenReturn(clusterState);
    }

    private DiscoveryNode createDataNode(Version version) {
        return new DiscoveryNode(
            "dataNode",
            "dataNodeId",
            buildNewFakeTransportAddress(),
            Collections.emptyMap(),
            Collections.singleton(DiscoveryNodeRole.DATA_ROLE),
            version
        );
    }

    private void setupClusterState(DiscoveryNode node, boolean hasMLJobsIndex) {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node).build();

        when(event.state()).thenReturn(clusterState);
        when(event.previousState()).thenReturn(clusterState);
        when(event.nodesDelta()).thenReturn(mock(DiscoveryNodes.Delta.class));
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);
        when(metadata.hasIndex(ML_JOBS_INDEX)).thenReturn(hasMLJobsIndex);
        when(metadata.settings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
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
    }
}
