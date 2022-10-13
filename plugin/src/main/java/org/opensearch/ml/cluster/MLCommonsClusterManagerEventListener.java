/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.plugin.MachineLearningPlugin.TASK_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;

import lombok.extern.log4j.Log4j2;

import org.opensearch.client.Client;
import org.opensearch.cluster.LocalNodeClusterManagerListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.component.LifecycleListener;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;

@Log4j2
public class MLCommonsClusterManagerEventListener implements LocalNodeClusterManagerListener {

    private final ClusterService clusterService;
    private Client client;
    private Settings settings;

    private final MLModelManager mlModelManager;
    private final MLTaskManager mlTaskManager;

    private ThreadPool threadPool;
    private Scheduler.Cancellable syncModelRoutingCron;
    private DiscoveryNodeHelper nodeHelper;

    private volatile Integer jobInterval;

    public MLCommonsClusterManagerEventListener(
        ClusterService clusterService,
        Client client,
        Settings settings,
        ThreadPool threadPool,
        MLModelManager mlModelManager,
        MLTaskManager mlTaskManager,
        DiscoveryNodeHelper nodeHelper
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.settings = settings;
        this.threadPool = threadPool;
        this.clusterService.addListener(this);
        this.mlModelManager = mlModelManager;
        this.mlTaskManager = mlTaskManager;
        this.nodeHelper = nodeHelper;

        this.jobInterval = ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.get(settings);
        clusterService.getClusterSettings().addSettingsUpdateConsumer(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS, it -> {
            jobInterval = it;
            cancel(syncModelRoutingCron);
            if (jobInterval <= 0) {
                log.debug("Stop ML syncup job as its interval is <=0");
            } else {
                startSyncModelRoutingCron();
            }
        });
    }

    @Override
    public void onClusterManager() {
        if (syncModelRoutingCron == null) {
            startSyncModelRoutingCron();
        }
    }

    private void startSyncModelRoutingCron() {
        syncModelRoutingCron = threadPool
            .scheduleWithFixedDelay(new MLSyncUpCron(client, nodeHelper), TimeValue.timeValueSeconds(jobInterval), TASK_THREAD_POOL);
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
