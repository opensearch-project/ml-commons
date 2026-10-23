/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

/**
 * A new cluster manager must always end up running the sync up job, even when the auto redeployer never signals it.
 */
public class MLCommonsClusterManagerEventListenerTests extends OpenSearchTestCase {

    @Mock
    private ClusterService clusterService;
    @Mock
    private ThreadPool threadPool;
    @Mock
    private MLModelAutoReDeployer mlModelAutoReDeployer;

    private MLCommonsClusterManagerEventListener listener;
    private final List<Runnable> scheduled = new ArrayList<>();
    private final List<TimeValue> scheduledDelays = new ArrayList<>();
    private DiscoveryNode localNode;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().put(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.getKey(), 10).build();
        when(clusterService.getClusterSettings()).thenReturn(clusterSetting(settings, ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS));
        localNode = new DiscoveryNode("cm", buildNewFakeTransportAddress(), Version.CURRENT);
        when(clusterService.localNode()).thenReturn(localNode);
        when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(inv -> {
            scheduled.add(inv.getArgument(0));
            scheduledDelays.add(inv.getArgument(1));
            return mock(Scheduler.ScheduledCancellable.class);
        });
        when(threadPool.scheduleWithFixedDelay(any(Runnable.class), any(TimeValue.class), anyString()))
            .thenReturn(mock(Scheduler.Cancellable.class));
        listener = new MLCommonsClusterManagerEventListener(
            clusterService,
            mock(Client.class),
            mock(SdkClient.class),
            settings,
            threadPool,
            mock(DiscoveryNodeHelper.class),
            mock(MLIndicesHandler.class),
            mock(Encryptor.class),
            mlModelAutoReDeployer,
            mock(MLFeatureEnabledSetting.class)
        );
    }

    private void electedClusterManager(boolean elected) {
        DiscoveryNodes.Builder nodes = DiscoveryNodes.builder().add(localNode).localNodeId(localNode.getId());
        if (elected) {
            nodes.clusterManagerNodeId(localNode.getId());
        }
        when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).nodes(nodes).build());
    }

    private ActionListener<Boolean> startCronListener() {
        ArgumentCaptor<ActionListener<Boolean>> captor = ArgumentCaptor.forClass(ActionListener.class);
        verify(mlModelAutoReDeployer).setStartCronJobListener(captor.capture());
        return captor.getValue();
    }

    private void verifyCronStarted(int times) {
        verify(threadPool, times(times)).scheduleWithFixedDelay(any(MLSyncUpCron.class), eq(TimeValue.timeValueSeconds(10)), anyString());
    }

    public void testOnClusterManager_schedulesRedeployAndFallback() {
        listener.onClusterManager();
        assertEquals(2, scheduled.size());
        assertEquals(TimeValue.timeValueSeconds(10), scheduledDelays.get(0));
        assertEquals(TimeValue.timeValueSeconds(60), scheduledDelays.get(1));
        verifyCronStarted(0);
    }

    public void testFallback_startsCronWhenRedeployerNeverSignals() {
        electedClusterManager(true);
        listener.onClusterManager();
        scheduled.get(1).run();
        verifyCronStarted(1);
    }

    public void testFallback_noSecondCronWhenRedeployerAlreadyStartedIt() {
        electedClusterManager(true);
        listener.onClusterManager();
        startCronListener().onResponse(true);
        scheduled.get(1).run();
        verifyCronStarted(1);
    }

    public void testRedeployerSignal_startsCronOnce() {
        electedClusterManager(true);
        listener.onClusterManager();
        ActionListener<Boolean> startCron = startCronListener();
        startCron.onResponse(true);
        startCron.onFailure(new RuntimeException("again"));
        verifyCronStarted(1);
    }

    public void testFallback_doesNothingAfterLosingClusterManager() {
        electedClusterManager(false);
        listener.onClusterManager();
        listener.offClusterManager();
        scheduled.get(1).run();
        verifyCronStarted(0);
        verify(threadPool, never()).scheduleWithFixedDelay(any(), any(), anyString());
    }

    public void testRedeployerSignal_doesNothingAfterLosingClusterManager() {
        electedClusterManager(true);
        listener.onClusterManager();
        ActionListener<Boolean> startCron = startCronListener();
        electedClusterManager(false);
        listener.offClusterManager();
        // the redeployer reads the models only now and signals with the listener of the term that ended
        startCron.onResponse(true);
        verify(threadPool, never()).scheduleWithFixedDelay(any(), any(), anyString());
    }
}
