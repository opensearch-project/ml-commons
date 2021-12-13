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

import static org.opensearch.ml.stats.StatNames.ML_EXECUTING_TASK_COUNT;

import org.opensearch.action.ActionListener;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.transport.TransportService;

/**
 * MLTaskRunner has common code for dispatching and running predict/training tasks.
 * @param <Request> ML task request
 * @param <Response> ML task request
 */
public abstract class MLTaskRunner<Request, Response> {
    protected final MLTaskManager mlTaskManager;
    protected final MLStats mlStats;
    protected final MLTaskDispatcher mlTaskDispatcher;

    protected static final String TASK_ID = "task_id";
    protected static final String ALGORITHM = "algorithm";
    protected static final String MODEL_NAME = "model_name";
    protected static final String MODEL_VERSION = "model_version";
    protected static final String MODEL_CONTENT = "model_content";
    protected static final String USER = "user";

    public MLTaskRunner(MLTaskManager mlTaskManager, MLStats mlStats, MLTaskDispatcher mlTaskDispatcher) {
        this.mlTaskManager = mlTaskManager;
        this.mlStats = mlStats;
        this.mlTaskDispatcher = mlTaskDispatcher;
    }

    protected void handleMLTaskFailure(MLTask mlTask, Exception e) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.FAILED
        // update task error
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.FAILED);
        mlTaskManager.updateTaskError(mlTask.getTaskId(), e.getMessage());
    }

    protected void handleMLTaskComplete(MLTask mlTask) {
        // decrease ML_EXECUTING_TASK_COUNT
        // update task state to MLTaskState.COMPLETED
        mlStats.getStat(ML_EXECUTING_TASK_COUNT.getName()).decrement();
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.COMPLETED);
    }

    public abstract void run(Request request, TransportService transportService, ActionListener<Response> listener);
}
