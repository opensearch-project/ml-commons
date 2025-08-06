/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.LAST_UPDATE_TIME_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.common.MLTask.TASK_TYPE_FIELD;
import static org.opensearch.ml.common.MLTaskState.CREATED;
import static org.opensearch.ml.common.MLTaskState.RUNNING;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.jobscheduler.spi.schedule.IntervalSchedule;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.jobs.MLJobParameter;
import org.opensearch.ml.jobs.MLJobType;
import org.opensearch.remote.metadata.client.PutDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.UpdateDataObjectRequest;
import org.opensearch.remote.metadata.client.UpdateDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.Client;
import org.opensearch.transport.client.Requests;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import lombok.extern.log4j.Log4j2;

/**
 * MLTaskManager is responsible for managing MLTask.
 */
@Log4j2
public class MLTaskManager {
    public static int TASK_SEMAPHORE_TIMEOUT = 5000; // 5 seconds
    private final Map<String, MLTaskCache> taskCaches;
    private final Client client;
    private final SdkClient sdkClient;
    private final ThreadPool threadPool;
    private final MLIndicesHandler mlIndicesHandler;
    private final Map<MLTaskType, AtomicInteger> runningTasksCount;
    private boolean taskPollingJobStarted;
    private boolean statsCollectorJobStarted;
    public static final ImmutableSet<MLTaskState> TASK_DONE_STATES = ImmutableSet
        .of(MLTaskState.COMPLETED, MLTaskState.COMPLETED_WITH_ERROR, MLTaskState.FAILED, MLTaskState.CANCELLED);

    /**
     * Constructor to create ML task manager.
     *
     * @param client client
     * @param mlIndicesHandler ML indices handler
     */
    public MLTaskManager(Client client, SdkClient sdkClient, ThreadPool threadPool, MLIndicesHandler mlIndicesHandler) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.threadPool = threadPool;
        this.mlIndicesHandler = mlIndicesHandler;
        taskCaches = new ConcurrentHashMap<>();
        runningTasksCount = new ConcurrentHashMap<>();
    }

    public synchronized void checkLimitAndAddRunningTask(MLTask mlTask, Integer limit) {
        AtomicInteger runningTaskCount = runningTasksCount.computeIfAbsent(mlTask.getTaskType(), it -> new AtomicInteger(0));
        if (runningTaskCount.get() < 0) {
            runningTaskCount.set(0);
        }
        log.debug("Task id: {}, current running task {}: {}", mlTask.getTaskId(), mlTask.getTaskType(), runningTaskCount.get());
        if (runningTaskCount.get() >= limit) {
            String error = "exceed max running task limit";
            log.warn("{} for task {}", error, mlTask.getTaskId());
            throw new MLLimitExceededException(error);
        }
        if (contains(mlTask.getTaskId())) {
            getMLTask(mlTask.getTaskId()).setState(RUNNING);
        } else {
            mlTask.setState(RUNNING);
            add(mlTask);
        }
        runningTaskCount.incrementAndGet();
    }

    public synchronized void checkMaxBatchJobTask(MLTaskType mlTaskType, Integer maxTaskLimit, ActionListener<Boolean> listener) {
        try {
            BoolQueryBuilder boolQuery = QueryBuilders
                .boolQuery()
                .must(QueryBuilders.termQuery(TASK_TYPE_FIELD, mlTaskType.name()))
                .must(
                    QueryBuilders
                        .boolQuery()
                        .should(QueryBuilders.termQuery(STATE_FIELD, CREATED))
                        .should(QueryBuilders.termQuery(STATE_FIELD, RUNNING))
                );

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(boolQuery);
            SearchRequest searchRequest = new SearchRequest(ML_TASK_INDEX);
            searchRequest.source(searchSourceBuilder);

            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<SearchResponse> internalListener = ActionListener.runBefore(ActionListener.wrap(searchResponse -> {
                    long matchedCount = searchResponse.getHits().getHits().length;
                    Boolean exceedLimit = false;
                    if (matchedCount >= maxTaskLimit) {
                        exceedLimit = true;
                    }
                    listener.onResponse(exceedLimit);
                }, listener::onFailure), () -> threadContext.restore());

                client.admin().indices().refresh(Requests.refreshRequest(ML_TASK_INDEX), ActionListener.wrap(refreshResponse -> {
                    client.search(searchRequest, internalListener);
                }, e -> {
                    log.error("Failed to refresh Task index during search MLTaskType for {}", mlTaskType, e);
                    internalListener.onFailure(e);
                }));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to search ML task for {}", mlTaskType, e);
            listener.onFailure(e);
        }
    }

    /**
     * Put ML task into cache.
     * If ML task is already in cache, will throw {@link IllegalArgumentException}
     *
     * @param mlTask ML task
     */
    public synchronized void add(MLTask mlTask) {
        // todo: once circuit break is in place, we need to add those checks
        // to make sure we have some limitation while adding new tasks.
        add(mlTask, null);
    }

    public synchronized void add(MLTask mlTask, List<String> workerNodes) {
        String taskId = mlTask.getTaskId();
        if (contains(taskId)) {
            throw new IllegalArgumentException("Duplicate taskId");
        }
        taskCaches.put(taskId, new MLTaskCache(mlTask, workerNodes));
        log.debug("add ML task to cache, taskId: {}, taskType: {} ", taskId, mlTask.getTaskType());
    }

    /**
     * Check if task exists in cache.
     *
     * @param taskId task id
     * @return true if task exists in cache; otherwise, return false.
     */
    public boolean contains(String taskId) {
        return taskCaches.containsKey(taskId);
    }

    /**
     * Remove task from cache.
     *
     * @param taskId ML task id
     */
    public void remove(String taskId) {
        if (contains(taskId)) {
            MLTaskCache taskCache = taskCaches.remove(taskId);
            MLTask mlTask = taskCache.getMlTask();

            if (mlTask.getState() != CREATED) {
                // Task initial state is CREATED. It will move forward to RUNNING state once it starts on worker node.
                // When finished or failed, it's possible to move to COMPLETED/FAILED state.
                // So if its state is not CREATED when remove it, the task already started on worker node, we should
                // decrement running task count.
                AtomicInteger runningTaskCount = runningTasksCount.get(mlTask.getTaskType());
                if (runningTaskCount != null) {
                    runningTaskCount.decrementAndGet();
                }
            }
            log.debug("remove ML task from cache {}", taskId);
        }
    }

    /**
     * Get task from cache.
     *
     * @param taskId ML task id
     * @return ML task
     */
    public MLTask getMLTask(String taskId) {
        if (contains(taskId)) {
            return taskCaches.get(taskId).getMlTask();
        }
        return null;
    }

    public MLTaskCache getMLTaskCache(String taskId) {
        if (contains(taskId)) {
            return taskCaches.get(taskId);
        }
        return null;
    }

    public Set<String> getWorkNodes(String taskId) {
        if (taskCaches.containsKey(taskId)) {
            return taskCaches.get(taskId).getWorkerNodes();
        }
        return null;
    }

    public void addNodeError(String taskId, String workerNodeId, String error) {
        log.debug("add task error: taskId: {}, workerNodeId: {}, error: {}", taskId, workerNodeId, error);
        if (taskCaches.containsKey(taskId)) {
            taskCaches.get(taskId).addError(workerNodeId, error);
        }
    }

    /**
     * Get all taskIds from cache
     * @return an array of all the keys in the taskCaches
     */
    public String[] getAllTaskIds() {
        return Strings.toStringArray(taskCaches.keySet());
    }

    /**
     * Get running task count in cache.
     *
     * @return running task count
     */
    public int getRunningTaskCount() {
        int res = 0;
        for (Map.Entry<String, MLTaskCache> entry : taskCaches.entrySet()) {
            MLTask mlTask = entry.getValue().getMlTask();
            if (mlTask.getState() != null && mlTask.getState() == RUNNING) {
                res++;
            }
        }
        return res;
    }

    /**
     * Clear all tasks.
     */
    public void clear() {
        taskCaches.clear();
    }

    /**
     * Create ML task. Will init ML task index first if absent.
     * @param mlTask ML task
     * @param listener action listener
     */
    public void createMLTask(MLTask mlTask, ActionListener<IndexResponse> listener) {
        mlIndicesHandler.initMLTaskIndex(ActionListener.wrap(indexCreated -> {
            if (!indexCreated) {
                listener.onFailure(new RuntimeException("No response to create ML task index"));
                return;
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {

                sdkClient
                    .putDataObjectAsync(
                        PutDataObjectRequest.builder().index(ML_TASK_INDEX).tenantId(mlTask.getTenantId()).dataObject(mlTask).build()
                    )
                    .whenComplete((r, throwable) -> {
                        context.restore();
                        if (throwable != null) {
                            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                            log.error("Failed to index ML task", cause);
                            listener.onFailure(cause);
                        } else {
                            try {
                                IndexResponse indexResponse = r.indexResponse();
                                log.info("Task creation result: {}, Task id: {}", indexResponse.getResult(), indexResponse.getId());
                                listener.onResponse(indexResponse);
                            } catch (Exception e) {
                                listener.onFailure(e);
                            }
                        }
                    });
            } catch (Exception e) {
                log.error("Failed to create ML task for {}, {}", mlTask.getFunctionName(), mlTask.getTaskType(), e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to create ML task index", e);
            listener.onFailure(e);
        }));
    }

    public void updateTaskStateAsRunning(String taskId, String tenantId, boolean isAsyncTask) {
        if (!contains(taskId)) {
            throw new IllegalArgumentException("Task not found");
        }
        MLTask task = getMLTask(taskId);
        task.setState(RUNNING);
        if (isAsyncTask) {
            updateMLTask(taskId, tenantId, ImmutableMap.of(STATE_FIELD, RUNNING), TASK_SEMAPHORE_TIMEOUT, false);
        }
    }

    /**
     * Update ML task with default listener.
     * @param taskId task id
     * @param tenantId tenant id
     * @param updatedFields updated field and values
     * @param timeoutInMillis time out waiting for updating task semaphore, zero or negative means don't wait at all
     * @param removeFromCache remove ML task from cache
     */
    public void updateMLTask(
        String taskId,
        String tenantId,
        Map<String, Object> updatedFields,
        long timeoutInMillis,
        boolean removeFromCache
    ) {
        ActionListener<UpdateResponse> internalListener = ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML task successfully: {}, taskId: {}, updatedFields: {}", response.status(), taskId, updatedFields);
            } else {
                log.error("Failed to update ML task {}, status: {}, updatedFields: {}", taskId, response.status(), updatedFields);
            }
        }, e -> { logException("Failed to update ML task: " + taskId, e, log); });
        updateMLTask(taskId, tenantId, updatedFields, internalListener, timeoutInMillis, removeFromCache);
    }

    /**
     * Update ML task.
     * @param taskId task id
     * @param tenantId tenant id
     * @param updatedFields updated field and values
     * @param listener action listener
     * @param timeoutInMillis time out waiting for updating task semaphore, zero or negative means don't wait at all
     * @param removeFromCache remove ML task from cache
     */
    public void updateMLTask(
        String taskId,
        String tenantId,
        Map<String, Object> updatedFields,
        ActionListener<UpdateResponse> listener,
        long timeoutInMillis,
        boolean removeFromCache
    ) {
        MLTaskCache taskCache = taskCaches.get(taskId);
        if (removeFromCache) {
            remove(taskId);
        }
        if (taskCache == null) {
            listener.onFailure(new MLResourceNotFoundException("Can't find task in cache: " + taskId));
            return;
        }
        threadPool.executor(GENERAL_THREAD_POOL).execute(() -> {
            Semaphore semaphore = taskCache.getUpdateTaskIndexSemaphore();
            try {
                if (semaphore != null && !semaphore.tryAcquire(timeoutInMillis, TimeUnit.MILLISECONDS)) {
                    listener.onFailure(new MLException("Other updating request not finished yet"));
                    return;
                }
            } catch (InterruptedException e) {
                log.error("Failed to acquire semaphore for ML task {}", taskId, e);
                listener.onFailure(e);
                return; // return directly if can't get semaphore
            }
            try {
                if (updatedFields == null || updatedFields.isEmpty()) {
                    listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                    return;
                }
                Map<String, Object> updatedContent = new HashMap<>(updatedFields);
                updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());

                UpdateDataObjectRequest.Builder requestBuilder = UpdateDataObjectRequest
                    .builder()
                    .index(ML_TASK_INDEX)
                    .id(taskId)
                    .tenantId(tenantId)
                    .dataObject(updatedContent);
                // Conditionally add retryOnConflict based on the provided condition
                if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains(updatedFields.containsKey(STATE_FIELD))) {
                    requestBuilder.retryOnConflict(3);
                }

                // Build the request
                UpdateDataObjectRequest updateDataObjectRequest = requestBuilder.build();

                ActionListener<UpdateResponse> actionListener = semaphore == null
                    ? listener
                    : ActionListener.runAfter(listener, semaphore::release);
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    sdkClient.updateDataObjectAsync(updateDataObjectRequest).whenComplete((r, throwable) -> {
                        context.restore(); // Restore the context once the operation is done
                        if (semaphore != null) {
                            semaphore.release();
                        }
                        handleUpdateDataObjectCompletionStage(r, throwable, getUpdateResponseListener(taskId, listener));
                    });
                } catch (Exception e) {
                    log.error("Failed to update ML task {}", taskId, e);
                    actionListener.onFailure(e);
                }
            } catch (Exception e) {
                if (semaphore != null) {
                    semaphore.release();
                }
                log.error("Failed to update ML task {}", taskId, e);
                listener.onFailure(e);
            }
        });
    }

    public void updateMLTaskDirectly(String taskId, Map<String, Object> updatedFields) {
        updateMLTaskDirectly(taskId, updatedFields, ActionListener.wrap(r -> { log.debug("updated ML task directly: {}", taskId); }, e -> {
            log.error("Failed to update ML task {}", taskId, e);
        }));
    }

    public void updateMLTaskDirectly(String taskId, Map<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        try {
            if (taskId == null || taskId.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Task ID is null or empty"));
                return;
            }

            if (updatedFields == null || updatedFields.isEmpty()) {
                listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                return;
            }

            if (updatedFields.containsKey(STATE_FIELD) && !(updatedFields.get(STATE_FIELD) instanceof MLTaskState)) {
                listener.onFailure(new IllegalArgumentException("Invalid task state"));
                return;
            }

            UpdateRequest updateRequest = new UpdateRequest(ML_TASK_INDEX, taskId);
            Map<String, Object> updatedContent = new HashMap<>(updatedFields);
            updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
            updateRequest.doc(updatedContent);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains((MLTaskState) updatedFields.get(STATE_FIELD))) {
                updateRequest.retryOnConflict(3);
            }

            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to update ML task {}", taskId, e);
            listener.onFailure(e);
        }
    }

    public boolean containsModel(String modelId) {
        for (Map.Entry<String, MLTaskCache> entry : taskCaches.entrySet()) {
            if (modelId.equals(entry.getValue().mlTask.getModelId())) {
                return true;
            }
        }
        return false;
    }

    public List<String[]> getLocalRunningDeployModelTasks() {
        List<String> runningDeployModelTaskIds = new ArrayList<>();
        List<String> runningDeployModelIds = new ArrayList<>();
        for (Map.Entry<String, MLTaskCache> entry : taskCaches.entrySet()) {
            MLTask mlTask = entry.getValue().getMlTask();
            if (mlTask.getTaskType() == MLTaskType.DEPLOY_MODEL && mlTask.getState() != CREATED) {
                runningDeployModelTaskIds.add(entry.getKey());
                runningDeployModelIds.add(mlTask.getModelId());
            }
        }
        return Arrays.asList(runningDeployModelTaskIds.toArray(new String[0]), runningDeployModelIds.toArray(new String[0]));
    }

    private void handleUpdateDataObjectCompletionStage(
        UpdateDataObjectResponse r,
        Throwable throwable,
        ActionListener<UpdateResponse> updateListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            updateListener.onFailure(cause);
        } else {
            try {
                updateListener.onResponse(r.updateResponse());
            } catch (Exception e) {
                updateListener.onFailure(e);
            }
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(String taskId, ActionListener<UpdateResponse> actionListener) {
        return ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.error("Failed to update the task with ID: {}", taskId);
                actionListener.onResponse(updateResponse);
                return;
            }
            log.info("Successfully updated the task with ID: {}", taskId);
            actionListener.onResponse(updateResponse);
        }, exception -> {
            log.error("Failed to update ML task with ID {}. Details: {}", taskId, exception);
            actionListener.onFailure(exception);
        });
    }

    public void startTaskPollingJob() {
        if (this.taskPollingJobStarted) {
            return;
        }

        try {
            MLJobParameter jobParameter = new MLJobParameter(
                MLJobType.BATCH_TASK_UPDATE.name(),
                new IntervalSchedule(Instant.now(), 1, ChronoUnit.MINUTES),
                20L,
                null,
                MLJobType.BATCH_TASK_UPDATE
            );

            IndexRequest indexRequest = new IndexRequest()
                .index(CommonValue.ML_JOBS_INDEX)
                .id(MLJobType.BATCH_TASK_UPDATE.name())
                .source(jobParameter.toXContent(JsonXContent.contentBuilder(), null))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            startJob(indexRequest, MLJobType.BATCH_TASK_UPDATE, () -> this.taskPollingJobStarted = true);
        } catch (IOException e) {
            log.error("Failed to index task polling job", e);
        }
    }

    public void startStatsCollectorJob() {
        if (statsCollectorJobStarted) {
            return;
        }

        try {
            MLJobParameter jobParameter = new MLJobParameter(
                MLJobType.STATS_COLLECTOR.name(),
                new IntervalSchedule(Instant.now(), 5, ChronoUnit.MINUTES),
                60L,
                null,
                MLJobType.STATS_COLLECTOR
            );

            IndexRequest indexRequest = new IndexRequest()
                .index(CommonValue.ML_JOBS_INDEX)
                .id(MLJobType.STATS_COLLECTOR.name())
                .source(jobParameter.toXContent(JsonXContent.contentBuilder(), null))
                .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

            startJob(indexRequest, MLJobType.STATS_COLLECTOR, () -> this.statsCollectorJobStarted = true);
        } catch (IOException e) {
            log.error("Failed to index stats collection job", e);
        }
    }

    /**
     * Start a job by indexing the job parameter to ML jobs index.
     * 
     * @param indexRequest the index request containing the job parameter
     * @param jobType the type of job being started
     * @param successCallback callback to execute on successful job indexing
     */
    private void startJob(IndexRequest indexRequest, MLJobType jobType, Runnable successCallback) {
        mlIndicesHandler.initMLJobsIndex(ActionListener.wrap(success -> {
            if (success) {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    client.index(indexRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                        log.info("Indexed {} successfully", jobType.name());
                        if (successCallback != null) {
                            successCallback.run();
                        }
                    }, e -> log.error("Failed to index {} job", jobType.name(), e)), context::restore));
                }
            }
        }, e -> log.error("Failed to initialize ML jobs index", e)));
    }
}
