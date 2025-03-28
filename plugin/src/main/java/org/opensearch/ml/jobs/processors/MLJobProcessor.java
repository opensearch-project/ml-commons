package org.opensearch.ml.jobs.processors;

import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;

public abstract class MLJobProcessor {

    private static final Logger log = LogManager.getLogger(MLJobProcessor.class);

    protected ClusterService clusterService;
    protected Client client;
    protected ThreadPool threadPool;

    public MLJobProcessor(ClusterService clusterService, Client client, ThreadPool threadPool) {
        this.clusterService = clusterService;
        this.client = client;
        this.threadPool = threadPool;
    }

    public abstract void run();

    public void process(ScheduledJobParameter scheduledJobParameter, JobExecutionContext jobExecutionContext) {
        final LockService lockService = jobExecutionContext.getLockService();

        Runnable runnable = () -> lockService.acquireLock(scheduledJobParameter, jobExecutionContext, ActionListener.wrap(lock -> {
            if (lock == null) {
                return;
            }

            try {
                log.info("Starting job execution for job ID: {} at {}", scheduledJobParameter.getName(), Instant.now());
                this.run();
                log.info("Completed job execution for job ID: {} at {}", scheduledJobParameter.getName(), Instant.now());
            } finally {
                lockService
                    .release(
                        lock,
                        ActionListener
                            .wrap(released -> log.debug("Released lock for job {}", scheduledJobParameter.getName()), exception -> {
                                throw new IllegalStateException("Failed to release lock.");
                            })
                    );
            }
        }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));

        threadPool.generic().submit(runnable);
    }
}
