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

import java.util.Collections;

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
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
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

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
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

    public void testClusterChanged_MemoryRetentionJobNotStarted_WhenMultiTenancyEnabled() {
        setupClusterState(false, createDataNode("n1", Version.CURRENT));
        when(clusterService.getSettings())
            .thenReturn(org.opensearch.common.settings.Settings.builder().put("plugins.ml_commons.multi_tenancy_enabled", true).build());

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
    }
}
