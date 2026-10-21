/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.cluster;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import java.util.List;

import org.opensearch.cluster.LocalNodeClusterManagerListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.lifecycle.LifecycleListener;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.autoredeploy.MLModelAutoReDeployer;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.engine.encryptor.Encryptor;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.Scheduler;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLCommonsClusterManagerEventListener implements LocalNodeClusterManagerListener {

    private final ClusterService clusterService;
    private Client client;
    private final SdkClient sdkClient;

    private ThreadPool threadPool;
    private Scheduler.Cancellable syncModelRoutingCron;
    private DiscoveryNodeHelper nodeHelper;
    private final MLIndicesHandler mlIndicesHandler;
    private final Encryptor encryptor;

    private volatile Integer jobInterval;

    private final MLModelAutoReDeployer mlModelAutoReDeployer;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public MLCommonsClusterManagerEventListener(
        ClusterService clusterService,
        Client client,
        SdkClient sdkClient,
        Settings settings,
        ThreadPool threadPool,
        DiscoveryNodeHelper nodeHelper,
        MLIndicesHandler mlIndicesHandler,
        Encryptor encryptor,
        MLModelAutoReDeployer modelAutoReDeployer,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.clusterService = clusterService;
        this.client = client;
        this.sdkClient = sdkClient;
        this.threadPool = threadPool;
        this.clusterService.addListener(this);
        this.nodeHelper = nodeHelper;
        this.mlIndicesHandler = mlIndicesHandler;
        this.encryptor = encryptor;
        this.mlModelAutoReDeployer = modelAutoReDeployer;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;

        this.jobInterval = ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS, this::restartSyncModelRoutingCron);
    }

    @Override
    public void onClusterManager() {
        ActionListener<Boolean> listener = ActionListener
            .wrap(r -> startSyncModelRoutingCronIfStillClusterManager(false), e -> startSyncModelRoutingCronIfStillClusterManager(false));
        mlModelAutoReDeployer.setStartCronJobListener(listener);
        String localNodeId = clusterService.localNode().getId();
        threadPool
            .schedule(
                () -> mlModelAutoReDeployer.buildAutoReloadArrangement(List.of(localNodeId), localNodeId),
                TimeValue.timeValueSeconds(jobInterval),
                GENERAL_THREAD_POOL
            );
        // The auto redeployer starts the sync up job once it has read the models to redeploy. Never let a new cluster
        // manager run without the sync up job (routing table, model state, task timeouts) if that signal is lost.
        threadPool
            .schedule(
                () -> startSyncModelRoutingCronIfStillClusterManager(true),
                TimeValue.timeValueSeconds(Math.max(SYNC_UP_CRON_FALLBACK_START_SECONDS, jobInterval * 6L)),
                GENERAL_THREAD_POOL
            );
    }

    static final long SYNC_UP_CRON_FALLBACK_START_SECONDS = 60;

    /**
     * Starts the sync up job unless it is already running or this node is no longer the elected cluster manager. Both
     * starters can fire late: the auto redeployer's signal belongs to the term it was registered in, so without the check
     * a node that lost cluster manager meanwhile would run the job next to the new cluster manager.
     */
    private synchronized void startSyncModelRoutingCronIfStillClusterManager(boolean fallback) {
        if (syncModelRoutingCron != null || !clusterService.state().nodes().isLocalNodeElectedClusterManager()) {
            return;
        }
        if (fallback) {
            log.warn("ML sync up job was not started by model auto redeploy, starting it now");
        }
        startSyncModelRoutingCron();
    }

    /**
     * The interval setting changes on every node. Only the elected cluster manager runs the sync up job; previously every
     * node started one here.
     */
    private synchronized void restartSyncModelRoutingCron(int interval) {
        jobInterval = interval;
        cancel(syncModelRoutingCron);
        syncModelRoutingCron = null;
        startSyncModelRoutingCronIfStillClusterManager(false);
    }

    private void startSyncModelRoutingCron() {
        if (jobInterval > 0) {
            log.info("Starting ML sync up job...");
            syncModelRoutingCron = threadPool
                .scheduleWithFixedDelay(
                    new MLSyncUpCron(client, sdkClient, clusterService, nodeHelper, mlIndicesHandler, encryptor, mlFeatureEnabledSetting),
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
    public synchronized void offClusterManager() {
        cancel(syncModelRoutingCron);
        syncModelRoutingCron = null;
    }

    private void cancel(Scheduler.Cancellable cron) {
        if (cron != null) {
            cron.cancel();
        }
    }
}
