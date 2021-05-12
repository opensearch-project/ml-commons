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

import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MLTaskManager is responsible for managing MLTask.
 */
public class MLTaskManager {
    private final Map<String, MLTask> taskCaches;
    // todo make this value configurable in the future
    public final static int MAX_ML_TASK_PER_NODE = 10;
    /**
     * Constructor to create ML task manager.
     *
     */
    public MLTaskManager() {
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
     * Update ML task state
     * @param taskId task id
     * @param state MLTaskState
     */
    public synchronized void updateTaskState(String taskId, MLTaskState state) {
        if (!contains(taskId)) {
            throw new IllegalArgumentException("Task not found");
        }
        taskCaches.get(taskId).setState(state);
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
}
