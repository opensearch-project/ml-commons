package org.opensearch.ml.jobs;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;

import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.jobscheduler.spi.JobExecutionContext;
import org.opensearch.jobscheduler.spi.ScheduledJobParameter;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.utils.LockService;
import org.opensearch.ml.common.transport.task.MLTaskGetAction;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.task.MLTaskManager;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;

public class MLBatchTaskUpdateJobRunner implements ScheduledJobRunner {
    private static final Logger log = LogManager.getLogger(ScheduledJobRunner.class);

    private static MLBatchTaskUpdateJobRunner INSTANCE;

    public static MLBatchTaskUpdateJobRunner getJobRunnerInstance() {
        if (INSTANCE != null) {
            return INSTANCE;
        }
        synchronized (MLBatchTaskUpdateJobRunner.class) {
            if (INSTANCE != null) {
                return INSTANCE;
            }
            INSTANCE = new MLBatchTaskUpdateJobRunner();
            return INSTANCE;
        }
    }

    private ClusterService clusterService;
    private ThreadPool threadPool;
    private Client client;
    private MLTaskManager taskManager;
    private boolean initialized;

    private MLBatchTaskUpdateJobRunner() {
        // Singleton class, use getJobRunner method instead of constructor
    }

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public void initialize(
        final ClusterService clusterService,
        final ThreadPool threadPool,
        final Client client,
        final MLTaskManager taskManager
    ) {
        this.clusterService = clusterService;
        this.threadPool = threadPool;
        this.client = client;
        this.taskManager = taskManager;
        this.initialized = true;
    }

    @Override
    public void runJob(ScheduledJobParameter scheduledJobParameter, JobExecutionContext jobExecutionContext) {
        if (initialized == false) {
            throw new AssertionError("this instance is not initialized");
        }

        final LockService lockService = jobExecutionContext.getLockService();

        lockService.acquireLock(scheduledJobParameter, jobExecutionContext, ActionListener.wrap(lock -> {
            if (lock == null) {
                return;
            }

            String jobName = scheduledJobParameter.getName();
            log.info("Starting job execution for job ID: {} at {}", jobName, Instant.now());

            if (taskManager == null) {
                log.error("TaskManager not initialized. Cannot run batch task polling job");
                return;
            }

            log.debug("Running batch task polling job");

            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            BoolQueryBuilder boolQuery = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.termQuery("task_type", "BATCH_PREDICTION"))
                .must(QueryBuilders.termQuery("function_name", "REMOTE"))
                .must(QueryBuilders.termQuery("state", "RUNNING"));

            sourceBuilder.query(boolQuery);
            sourceBuilder.size(100);
            sourceBuilder.fetchSource(new String[] { "_id" }, null);

            SearchRequest searchRequest = new SearchRequest(ML_TASK_INDEX);
            searchRequest.source(sourceBuilder);

            client.search(searchRequest, ActionListener.wrap(response -> {
                if (response == null || response.getHits() == null || response.getHits().getHits().length == 0) {
                    log.info("No pending tasks found to be polled by the job");
                    return;
                }

                SearchHit[] searchHits = response.getHits().getHits();
                for (SearchHit searchHit : searchHits) {
                    String taskId = searchHit.getId();
                    log.debug("Starting polling for task: {} at {}", taskId, Instant.now());
                    MLTaskGetRequest mlTaskGetRequest = MLTaskGetRequest.builder().taskId(taskId).isUserInitiatedGetRequest(false).build();

                    client.execute(MLTaskGetAction.INSTANCE, mlTaskGetRequest, ActionListener.wrap(taskResponse -> {
                        try {
                            log.info("Updated Task status for taskId: {} at {}", taskId, Instant.now());
                        } catch (Exception e) {
                            log.error("Failed to update task status for task: " + taskId, e);
                        }
                    }, exception -> {
                        log.error("Failed to get task status for task: " + taskId, exception);

                    }));
                }
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    log.info("No tasks found to be polled by the job");
                } else {
                    log.error("Failed to search for tasks to be polled by the job ", e);
                }
            }));

            log.info("Completed job execution for job ID: {} at {}", jobName, Instant.now());
            lockService
                .release(
                    lock,
                    ActionListener
                        .wrap(released -> { log.debug("Released lock for job {}", scheduledJobParameter.getName()); }, exception -> {
                            throw new IllegalStateException("Failed to release lock.");
                        })
                );
        }, exception -> { throw new IllegalStateException("Failed to acquire lock."); }));
    }
}
