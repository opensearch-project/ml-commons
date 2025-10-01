/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.TASK_POLLING_JOB_INDEX;

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

    public void testClusterChanged_WithV31DataNode_MetricCollectionEnabled() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, false);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexStatsCollectorJob(true);
        verify(mlTaskManager, never()).startTaskPollingJob();
    }

    public void testClusterChanged_WithV31DataNode_TaskPollingIndexExists() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_1_0);
        setupClusterState(dataNode, true);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(false);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
        verify(mlTaskManager).startTaskPollingJob();
    }

    public void testClusterChanged_WithPreV31DataNode_NoJobsStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_0_0);
        setupClusterState(dataNode, true);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager, never()).indexStatsCollectorJob(anyBoolean());
        verify(mlTaskManager, never()).startTaskPollingJob();
    }

    public void testClusterChanged_WithPostV31DataNode_JobsStarted() {
        DiscoveryNode dataNode = createDataNode(Version.V_3_2_0);
        setupClusterState(dataNode, true);

        when(mlFeatureEnabledSetting.isMetricCollectionEnabled()).thenReturn(true);
        when(mlFeatureEnabledSetting.isStaticMetricCollectionEnabled()).thenReturn(true);

        listener.clusterChanged(event);

        verify(mlTaskManager).indexStatsCollectorJob(true);
        verify(mlTaskManager).startTaskPollingJob();
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

    private void setupClusterState(DiscoveryNode node, boolean hasTaskPollingIndex) {
        DiscoveryNodes nodes = DiscoveryNodes.builder().add(node).build();

        when(event.state()).thenReturn(clusterState);
        when(event.previousState()).thenReturn(clusterState);
        when(event.nodesDelta()).thenReturn(mock(DiscoveryNodes.Delta.class));
        when(clusterState.nodes()).thenReturn(nodes);
        when(clusterState.getMetadata()).thenReturn(metadata);
        when(clusterService.state()).thenReturn(clusterState);
        when(metadata.hasIndex(TASK_POLLING_JOB_INDEX)).thenReturn(hasTaskPollingIndex);
        when(metadata.settings()).thenReturn(org.opensearch.common.settings.Settings.EMPTY);
    }
}
