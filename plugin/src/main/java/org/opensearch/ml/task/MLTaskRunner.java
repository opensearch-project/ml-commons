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

import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.stats.MLStats;

/**
 * MLTaskRunner has common code for dispatching and running predict/training/upload/search tasks.
 */
public class MLTaskRunner {
    protected final MLTaskManager mlTaskManager;
    protected final MLStats mlStats;
    protected final MLTaskDispatcher mlTaskDispatcher;

    // Now this is being used as the model id, change to the actually _id in system index later
    protected static final String TASK_ID = "taskId";
    protected static final String MODEL_ID = "_id";

    // Add this to model metadata later if see fit
    protected static final String CREATED_TIME = "createdTime";

    protected static final String ALGORITHM = "algorithm";
    protected static final String MODEL_VERSION = "modelVersion";
    protected static final String MODEL_NAME = "modelName";
    protected static final String MODEL_FORMAT = "modelFormat";
    protected static final String MODEL_CONTENT = "modelContent";

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
}
