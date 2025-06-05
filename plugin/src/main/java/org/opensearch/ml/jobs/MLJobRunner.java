/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.jobs.processors.MLBatchTaskUpdateProcessor;
import org.opensearch.ml.jobs.processors.MLStatsJobProcessor;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

import com.google.common.annotations.VisibleForTesting;

import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class MLJobRunner implements ScheduledJobRunner {

    private static MLJobRunner instance;

    public static MLJobRunner getInstance() {
        if (instance != null) {
            return instance;
        }
        synchronized (MLJobRunner.class) {
            if (instance != null) {
                return instance;
            }
            instance = new MLJobRunner();
            return instance;
        }
    }

    @Setter
    private ClusterService clusterService;

    @Setter
    private ThreadPool threadPool;

    @Setter
    private Client client;

    @Setter
    private SdkClient sdkClient;

    @Setter
    private ConnectorAccessControlHelper connectorAccessControlHelper;

    @Setter
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private boolean initialized;

    @VisibleForTesting
    MLJobRunner() {
        // Singleton class, use getJobRunner method instead of constructor
    }

    public void initialize(
        final ClusterService clusterService,
        final ThreadPool threadPool,
        final Client client,
        final SdkClient sdkClient,
        final ConnectorAccessControlHelper connectorAccessControlHelper,
        final MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.initialized = true;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public void runJob(ScheduledJobParameter scheduledJobParameter, JobExecutionContext jobExecutionContext) {
        if (!initialized) {
            throw new IllegalStateException("MLJobRunner Instance not initialized");
        }

        MLJobParameter jobParameter = (MLJobParameter) scheduledJobParameter;
        if (jobParameter == null || jobParameter.getJobType() == null) {
            throw new IllegalArgumentException("Job parameters is invalid.");
        }

        switch (jobParameter.getJobType()) {
            case STATS_COLLECTOR:
                MLStatsJobProcessor
                    .getInstance(clusterService, client, threadPool, connectorAccessControlHelper, sdkClient)
                    .process(jobParameter, jobExecutionContext, mlFeatureEnabledSetting.isStaticMetricCollectionEnabled());
                break;
            case BATCH_TASK_UPDATE:
                MLBatchTaskUpdateProcessor.getInstance(clusterService, client, threadPool).process(jobParameter, jobExecutionContext);
                break;
            default:
                throw new IllegalArgumentException("Unsupported job type " + jobParameter.getJobType());
        }
    }
}
