/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.task;

import static org.opensearch.ml.indices.MLIndicesHandler.ML_TASK_INDEX;
import static org.opensearch.ml.model.MLTask.LAST_UPDATE_TIME_FIELD;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.rest.RestStatus;

import com.google.common.collect.ImmutableMap;

/**
 * MLTaskManager is responsible for managing MLTask.
 */
@Log4j2
public class MLTaskManager {
    private final Map<String, MLTask> taskCaches;
    // todo make this value configurable in the future
    public final static int MAX_ML_TASK_PER_NODE = 10;
    private final Client client;
    private final MLIndicesHandler mlIndicesHandler;

    /**
     * Constructor to create ML task manager.
     *
     * @param client client
     * @param mlIndicesHandler ML indices handler
     */
    public MLTaskManager(Client client, MLIndicesHandler mlIndicesHandler) {
        this.client = client;
        this.mlIndicesHandler = mlIndicesHandler;
        taskCaches = new ConcurrentHashMap<>();
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
        String taskId = mlTask.getTaskId();
        if (contains(taskId)) {
            throw new IllegalArgumentException("Duplicate taskId");
        }
        taskCaches.put(taskId, mlTask);
    }

    /**
     * Put ML task into cache if it's not existed in the cache.
     *
     * @param mlTask ML task
     */
    public synchronized void addIfAbsent(MLTask mlTask) {
        String taskId = mlTask.getTaskId();
        if (!contains(taskId)) {
            taskCaches.put(taskId, mlTask);
        }
    }

    /**
     * Update ML task state
     * @param taskId task id
     * @param state MLTaskState
     */
    public synchronized void updateTaskState(String taskId, MLTaskState state) {
        if (!contains(taskId)) {
            throw new IllegalArgumentException("Task not found");
        }
        taskCaches.get(taskId).setState(state);
        updateMLTask(taskId, ImmutableMap.of(MLTask.STATE_FIELD, state));
    }

    /**
     * Update task error
     * @param taskId task id
     * @param error error message
     */
    public synchronized void updateTaskError(String taskId, String error) {
        if (!contains(taskId)) {
            throw new IllegalArgumentException("Task not found");
        }
        taskCaches.get(taskId).setError(error);
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
            taskCaches.remove(taskId);
        }
    }

    /**
     * Get task from cache.
     *
     * @param taskId ML task id
     * @return ML task
     */
    public MLTask get(String taskId) {
        if (contains(taskId)) {
            return taskCaches.get(taskId);
        }
        return null;
    }

    /**
     * Get running task count in cache.
     *
     * @return running task count
     */
    public int getRunningTaskCount() {
        int res = 0;
        for (Map.Entry<String, MLTask> entry : taskCaches.entrySet()) {
            if (entry.getValue().getState() != null && entry.getValue().getState() == MLTaskState.RUNNING) {
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
            try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
                request.source(mlTask.toXContent(builder, ToXContent.EMPTY_PARAMS)).setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                client.index(request, listener);
            } catch (Exception e) {
                log.error("Failed to create AD task for " + mlTask.getFunctionName() + ", " + mlTask.getTaskType(), e);
                listener.onFailure(e);
            }
        }, e -> {
            log.error("Failed to create ML index", e);
            listener.onFailure(e);
        }));
    }

    /**
     * Update ML task with default listener.
     * @param taskId task id
     * @param updatedFields updated field and values
     */
    public void updateMLTask(String taskId, Map<String, Object> updatedFields) {
        updateMLTask(taskId, updatedFields, ActionListener.wrap(response -> {
            if (response.status() == RestStatus.OK) {
                log.debug("Updated ML task successfully: {}, task id: {}", response.status(), taskId);
            } else {
                log.error("Failed to update ML task {}, status: {}", taskId, response.status());
            }
        }, e -> { log.error("Failed to update ML task: " + taskId, e); }));
    }

    /**
     * Update ML task.
     * @param taskId task id
     * @param updatedFields updated field and values
     * @param listener action listener
     */
    public void updateMLTask(String taskId, Map<String, Object> updatedFields, ActionListener<UpdateResponse> listener) {
        UpdateRequest updateRequest = new UpdateRequest(ML_TASK_INDEX, taskId);
        Map<String, Object> updatedContent = new HashMap<>();
        updatedContent.putAll(updatedFields);
        updatedContent.put(LAST_UPDATE_TIME_FIELD, Instant.now().toEpochMilli());
        updateRequest.doc(updatedContent);
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        client.update(updateRequest, listener);
    }
}
