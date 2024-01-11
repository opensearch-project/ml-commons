/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.LAST_UPDATE_TIME_FIELD;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.utils.MLExceptionUtils.logException;

import java.time.Instant;
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

import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.exception.MLLimitExceededException;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.threadpool.ThreadPool;

import lombok.extern.log4j.Log4j2;

/**
 * MLTaskManager is responsible for managing MLTask.
 */
@Log4j2
public class MLTaskManager {
    public static int TASK_SEMAPHORE_TIMEOUT = 5000; // 5 seconds
    private final Map<String, MLTaskCache> taskCaches;
    private final Client client;
    private final ThreadPool threadPool;
    private final MLIndicesHandler mlIndicesHandler;
    private final Map<MLTaskType, AtomicInteger> runningTasksCount;

    public static final Set TASK_DONE_STATES = Set
        .of(MLTaskState.COMPLETED, MLTaskState.COMPLETED_WITH_ERROR, MLTaskState.FAILED, MLTaskState.CANCELLED);

    /**
     * Constructor to create ML task manager.
     *
     * @param client client
     * @param mlIndicesHandler ML indices handler
     */
    public MLTaskManager(Client client, ThreadPool threadPool, MLIndicesHandler mlIndicesHandler) {
        this.client = client;
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
            log.warn(error + " for task " + mlTask.getTaskId());
            throw new MLLimitExceededException(error);
        }
        if (contains(mlTask.getTaskId())) {
            getMLTask(mlTask.getTaskId()).setState(MLTaskState.RUNNING);
        } else {
            mlTask.setState(MLTaskState.RUNNING);
            add(mlTask);
        }
        runningTaskCount.incrementAndGet();
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
        log.debug("add ML task to cache " + taskId);
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

            if (mlTask.getState() != MLTaskState.CREATED) {
                // Task initial state is CREATED. It will move forward to RUNNING state once it starts on worker node.
                // When finished or failed, it's possible to move to COMPLETED/FAILED state.
                // So if its state is not CREATED when remove it, the task already started on worker node, we should
                // decrement running task count.
                AtomicInteger runningTaskCount = runningTasksCount.get(mlTask.getTaskType());
                if (runningTaskCount != null) {
                    runningTaskCount.decrementAndGet();
                }
            }
            log.debug("remove ML task from cache " + taskId);
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
            if (mlTask.getState() != null && mlTask.getState() == MLTaskState.RUNNING) {
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
            IndexRequest request = new IndexRequest(ML_TASK_INDEX);
            try (
                XContentBuilder builder = XContentFactory.jsonBuilder();
                ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()
            ) {
                request.source(mlTask.toXContent(builder, ToXContent.EMPTY_PARAMS)).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(request, ActionListener.runBefore(listener, () -> context.restore()));
            } catch (Exception e) {
                log.error("Failed to create AD task for " + mlTask.getFunctionName() + ", " + mlTask.getTaskType(), e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to create ML index", e);
            listener.onFailure(e);
        }));
    }

    public void updateTaskStateAsRunning(String taskId, boolean isAsyncTask) {
        if (!contains(taskId)) {
            throw new IllegalArgumentException("Task not found");
        }
        MLTask task = getMLTask(taskId);
        task.setState(MLTaskState.RUNNING);
        if (isAsyncTask) {
            updateMLTask(taskId, Map.of(STATE_FIELD, MLTaskState.RUNNING), TASK_SEMAPHORE_TIMEOUT, false);
        }
    }

    /**
     * Update ML task with default listener.
     * @param taskId task id
     * @param updatedFields updated field and values
     * @param timeoutInMillis time out waiting for updating task semaphore, zero or negative means don't wait at all
     * @param removeFromCache remove ML task from cache
     */
    public void updateMLTask(String taskId, Map<String, Object> updatedFields, long timeoutInMillis, boolean removeFromCache) {
        ActionListener<UpdateResponse> internalListener = ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML task successfully: {}, taskId: {}, updatedFields: {}", response.status(), taskId, updatedFields);
            } else {
                log.error("Failed to update ML task {}, status: {}, updatedFields: {}", taskId, response.status(), updatedFields);
            }
        }, e -> { logException("Failed to update ML task: " + taskId, e, log); });
        updateMLTask(taskId, updatedFields, internalListener, timeoutInMillis, removeFromCache);
    }

    /**
     * Update ML task.
     * @param taskId task id
     * @param updatedFields updated field and values
     * @param listener action listener
     * @param timeoutInMillis time out waiting for updating task semaphore, zero or negative means don't wait at all
     * @param removeFromCache remove ML task from cache
     */
    public void updateMLTask(
        String taskId,
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
                log.error("Failed to acquire semaphore for ML task " + taskId, e);
                listener.onFailure(e);
                return; // return directly if can't get semaphore
            }
            try {
                if (updatedFields == null || updatedFields.size() == 0) {
                    listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                    return;
                }
                UpdateRequest updateRequest = new UpdateRequest(ML_TASK_INDEX, taskId);
                Map<String, Object> updatedContent = new HashMap<>();
                updatedContent.putAll(updatedFields);
                updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
                updateRequest.doc(updatedContent);
                updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains(updatedFields.containsKey(STATE_FIELD))) {
                    updateRequest.retryOnConflict(3);
                }
                ActionListener<UpdateResponse> actionListener = semaphore == null
                    ? listener
                    : ActionListener.runAfter(listener, () -> semaphore.release());
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    client.update(updateRequest, ActionListener.runBefore(actionListener, () -> context.restore()));
                } catch (Exception e) {
                    actionListener.onFailure(e);
                }
            } catch (Exception e) {
                semaphore.release();
                log.error("Failed to update ML task " + taskId, e);
                listener.onFailure(e);
            }
        });
    }

    public void updateMLTaskDirectly(String taskId, Map<String, Object> updatedFields) {
        updateMLTaskDirectly(taskId, updatedFields, ActionListener.wrap(r -> { log.debug("updated ML task directly: {}", taskId); }, e -> {
            log.error("Failed to update ML task " + taskId, e);
        }));
    }

    public void updateMLTaskDirectly(String taskId, Map<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        try {
            if (updatedFields == null || updatedFields.size() == 0) {
                listener.onFailure(new IllegalArgumentException("Updated fields is null or empty"));
                return;
            }
            UpdateRequest updateRequest = new UpdateRequest(ML_TASK_INDEX, taskId);
            Map<String, Object> updatedContent = new HashMap<>();
            updatedContent.putAll(updatedFields);
            updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
            updateRequest.doc(updatedContent);
            updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
            if (updatedFields.containsKey(STATE_FIELD) && TASK_DONE_STATES.contains(updatedFields.containsKey(STATE_FIELD))) {
                updateRequest.retryOnConflict(3);
            }
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, () -> context.restore()));
            } catch (Exception e) {
                listener.onFailure(e);
            }
        } catch (Exception e) {
            log.error("Failed to update ML task " + taskId, e);
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
            if (mlTask.getTaskType() == MLTaskType.DEPLOY_MODEL && mlTask.getState() != MLTaskState.CREATED) {
                runningDeployModelTaskIds.add(entry.getKey());
                runningDeployModelIds.add(mlTask.getModelId());
            }
        }
        return Arrays.asList(runningDeployModelTaskIds.toArray(new String[0]), runningDeployModelIds.toArray(new String[0]));
    }

}
