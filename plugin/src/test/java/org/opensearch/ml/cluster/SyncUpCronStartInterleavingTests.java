/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;
import static org.opensearch.ml.utils.TestHelper.clusterSetting;

import java.util.ArrayList;
import java.util.List;

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
 * Exhaustive interleavings of cluster manager changes on one node and the two ways the sync up job is started there: the
 * auto redeployer's signal (after it read the models) and the fallback timer. Invariants: never more than one sync up
 * job; once every pending signal and timer has fired, the job runs if and only if the node is the elected cluster
 * manager.
 */
public class SyncUpCronStartInterleavingTests extends OpenSearchTestCase {

    private enum Kind {
        ELECTED, // onClusterManager
        DEPOSED, // offClusterManager
        REDEPLOYER_SIGNAL, // the redeployer fires the listener it currently holds
        FALLBACK_TIMER, // one scheduled fallback runs
        REDEPLOY_TASK // the scheduled initial redeploy query runs (no-op here; the signal models its outcome)
    }

    private final class World {
        final ClusterService clusterService = mock(ClusterService.class);
        final ThreadPool threadPool = mock(ThreadPool.class);
        final MLModelAutoReDeployer redeployer = mock(MLModelAutoReDeployer.class);
        final DiscoveryNode localNode = new DiscoveryNode("cm", ADDRESS, Version.CURRENT);
        final MLCommonsClusterManagerEventListener listener;
        // what the real redeployer holds: the latest listener, cleared once fired
        ActionListener<Boolean> redeployerListener;
        final List<Runnable> fallbacks = new ArrayList<>();
        final List<Runnable> redeployTasks = new ArrayList<>();
        int activeCrons;
        int maxActiveCrons;
        boolean elected;
        int elections;

        World() {
            Settings settings = Settings.builder().put(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.getKey(), 10).build();
            when(clusterService.getClusterSettings()).thenReturn(clusterSetting(settings, ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS));
            when(clusterService.localNode()).thenReturn(localNode);
            publish();
            doAnswer(inv -> {
                redeployerListener = inv.getArgument(0);
                return null;
            }).when(redeployer).setStartCronJobListener(any());
            when(threadPool.schedule(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(inv -> {
                TimeValue delay = inv.getArgument(1);
                // the initial redeploy runs after one job interval, the fallback after at least 60s
                (delay.seconds() >= MLCommonsClusterManagerEventListener.SYNC_UP_CRON_FALLBACK_START_SECONDS ? fallbacks : redeployTasks)
                    .add(inv.getArgument(0));
                return mock(Scheduler.ScheduledCancellable.class);
            });
            when(threadPool.scheduleWithFixedDelay(any(Runnable.class), any(TimeValue.class), anyString())).thenAnswer(inv -> {
                activeCrons++;
                maxActiveCrons = Math.max(maxActiveCrons, activeCrons);
                Scheduler.Cancellable cancellable = mock(Scheduler.Cancellable.class);
                boolean[] cancelled = new boolean[1];
                when(cancellable.cancel()).thenAnswer(c -> {
                    if (!cancelled[0]) {
                        cancelled[0] = true;
                        activeCrons--;
                    }
                    return true;
                });
                return cancellable;
            });
            listener = new MLCommonsClusterManagerEventListener(
                clusterService,
                mock(Client.class),
                mock(SdkClient.class),
                settings,
                threadPool,
                mock(DiscoveryNodeHelper.class),
                mock(MLIndicesHandler.class),
                mock(Encryptor.class),
                redeployer,
                mock(MLFeatureEnabledSetting.class)
            );
        }

        void publish() {
            DiscoveryNodes.Builder nodes = DiscoveryNodes.builder().add(localNode).localNodeId(localNode.getId());
            if (elected) {
                nodes.clusterManagerNodeId(localNode.getId());
            }
            when(clusterService.state()).thenReturn(ClusterState.builder(new ClusterName("test")).nodes(nodes).build());
        }

        List<Kind> enabled() {
            List<Kind> kinds = new ArrayList<>();
            if (!elected && elections < 2) {
                kinds.add(Kind.ELECTED);
            }
            if (elected) {
                kinds.add(Kind.DEPOSED);
            }
            if (redeployerListener != null) {
                kinds.add(Kind.REDEPLOYER_SIGNAL);
            }
            if (!fallbacks.isEmpty()) {
                kinds.add(Kind.FALLBACK_TIMER);
            }
            if (!redeployTasks.isEmpty()) {
                kinds.add(Kind.REDEPLOY_TASK);
            }
            return kinds;
        }

        void apply(Kind kind) {
            switch (kind) {
                case ELECTED -> {
                    elected = true;
                    elections++;
                    publish();
                    listener.onClusterManager();
                }
                case DEPOSED -> {
                    elected = false;
                    publish();
                    listener.offClusterManager();
                }
                case REDEPLOYER_SIGNAL -> {
                    ActionListener<Boolean> l = redeployerListener;
                    redeployerListener = null;
                    l.onResponse(true);
                }
                case FALLBACK_TIMER -> fallbacks.remove(0).run();
                case REDEPLOY_TASK -> redeployTasks.remove(0).run();
            }
        }
    }

    private static final org.opensearch.core.common.transport.TransportAddress ADDRESS = buildNewFakeTransportAddress();
    private int sequences;
    private final List<String> violations = new ArrayList<>();

    public void testAllInterleavings() {
        explore(new ArrayList<>());
        logger.info("sync up cron start: {} complete sequences, {} violations", sequences, violations.size());
        assertTrue(
            violations.size() + " violations, first: " + violations.subList(0, Math.min(5, violations.size())),
            violations.isEmpty()
        );
    }

    private void explore(List<Kind> prefix) {
        World world = new World();
        for (Kind k : prefix) {
            world.apply(k);
            if (world.maxActiveCrons > 1) {
                violations.add("two sync up jobs running after " + prefix);
                return;
            }
        }
        List<Kind> next = world.enabled();
        // quiescent: nothing left to fire except (re-)elections/depositions
        boolean quiescent = world.redeployerListener == null && world.fallbacks.isEmpty() && world.redeployTasks.isEmpty();
        if (quiescent) {
            sequences++;
            int expected = world.elected ? 1 : 0;
            if (world.activeCrons != expected) {
                violations.add("elected=" + world.elected + " but " + world.activeCrons + " sync up jobs for " + prefix);
            }
        }
        for (Kind k : next) {
            prefix.add(k);
            explore(prefix);
            prefix.remove(prefix.size() - 1);
        }
    }
}
