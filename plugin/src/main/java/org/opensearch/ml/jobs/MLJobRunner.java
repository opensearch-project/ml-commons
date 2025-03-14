/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

import org.opensearch.cluster.service.ClusterService;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.ml.jobs.processors.MLBatchTaskUpdateProcessor;
import org.opensearch.ml.jobs.processors.MLStatsJobProcessor;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

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

    private boolean initialized;

    private MLJobRunner() {
        // Singleton class, use getJobRunner method instead of constructor
    }

    public void initialize(final ClusterService clusterService, final ThreadPool threadPool, final Client client) {
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.initialized = true;
    }

    @Override
    public void runJob(ScheduledJobParameter scheduledJobParameter, JobExecutionContext jobExecutionContext) {
        if (!initialized) {
            throw new IllegalStateException("MLJobRunner Instance not initialized");
        }

        MLJobParameter jobParameter = (MLJobParameter) scheduledJobParameter;
        switch (jobParameter.getJobType()) {
            case STATS_COLLECTOR:
                MLStatsJobProcessor.getInstance(clusterService, client, threadPool).process(jobParameter, jobExecutionContext);
                break;
            case BATCH_TASK_UPDATE:
                MLBatchTaskUpdateProcessor.getInstance(clusterService, client, threadPool).process(jobParameter, jobExecutionContext);
                break;
            default:
                throw new IllegalArgumentException("Unsupported job type " + jobParameter.getJobType());
        }
    }
}
