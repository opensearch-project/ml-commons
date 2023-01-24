/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;

import lombok.extern.log4j.Log4j2;

import org.opensearch.client.Client;
import org.opensearch.cluster.LocalNodeClusterManagerListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.component.LifecycleListener;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;

@Log4j2
public class MLCommonsClusterManagerEventListener implements LocalNodeClusterManagerListener {

    private final ClusterService clusterService;
    private Client client;

    private ThreadPool threadPool;
    private Scheduler.Cancellable syncModelRoutingCron;
    private DiscoveryNodeHelper nodeHelper;
    private final MLIndicesHandler mlIndicesHandler;

    private volatile Integer jobInterval;

    public MLCommonsClusterManagerEventListener(
        ClusterService clusterService,
        Client client,
        Settings settings,
        ThreadPool threadPool,
        DiscoveryNodeHelper nodeHelper,
        MLIndicesHandler mlIndicesHandler
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.threadPool = threadPool;
        this.clusterService.addListener(this);
        this.nodeHelper = nodeHelper;
        this.mlIndicesHandler = mlIndicesHandler;

        this.jobInterval = ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS, it -> {
            jobInterval = it;
            cancel(syncModelRoutingCron);
            startSyncModelRoutingCron();
        });
    }

    @Override
    public void onClusterManager() {
        if (syncModelRoutingCron == null) {
            startSyncModelRoutingCron();
        }
    }

    private void startSyncModelRoutingCron() {
        if (jobInterval > 0) {
            syncModelRoutingCron = threadPool
                .scheduleWithFixedDelay(
                    new MLSyncUpCron(client, clusterService, nodeHelper, mlIndicesHandler),
                    TimeValue.timeValueSeconds(jobInterval),
                    GENERAL_THREAD_POOL
                );
        } else {
            log.debug("Stop ML syncup job as its interval is: {}", jobInterval);
        }
        clusterService.addLifecycleListener(new LifecycleListener() {
            @Override
            public void beforeStop() {
                cancel(syncModelRoutingCron);
                syncModelRoutingCron = null;
            }
        });
    }

    @Override
    public void offClusterManager() {
        cancel(syncModelRoutingCron);
        syncModelRoutingCron = null;
    }

    private void cancel(Scheduler.Cancellable cron) {
        if (cron != null) {
            cron.cancel();
        }
    }
}
