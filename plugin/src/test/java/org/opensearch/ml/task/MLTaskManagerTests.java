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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.ml.model.MLTask;
import org.opensearch.ml.model.MLTaskState;
import org.opensearch.ml.model.MLTaskType;

import java.time.Instant;

public class MLTaskManagerTests {
    MLTaskManager mlTaskManager;
    MLTask mlTask;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        this.mlTaskManager = new MLTaskManager();
        this.mlTask = MLTask.builder()
                .taskId("task id")
                .taskType(MLTaskType.PREDICTION)
                .createTime(Instant.now())
                .state(MLTaskState.CREATED)
                .build();
    }

    @Test
    public void testAdd() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Duplicate taskId");
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.add(mlTask);
    }

    @Test
    public void testUpdateTaskState() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Task not found");
        mlTaskManager.add(mlTask);
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING);
        Assert.assertSame(mlTaskManager.get(mlTask.getTaskId()).getState(), MLTaskState.RUNNING);
        mlTaskManager.updateTaskState("not exist", MLTaskState.RUNNING);
    }

    @Test
    public void testUpdateTaskError() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Task not found");
        mlTaskManager.add(mlTask);
        mlTaskManager.updateTaskError(mlTask.getTaskId(), "error message");
        Assert.assertEquals("error message", mlTaskManager.get(mlTask.getTaskId()).getError());
        mlTaskManager.updateTaskError("not exist", "error message");
    }

    @Test
    public void testRemove() {
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.remove(mlTask.getTaskId());
        Assert.assertFalse(mlTaskManager.contains(mlTask.getTaskId()));
    }

    @Test
    public void testGetRunningTaskCount() {
        MLTask task1 = MLTask.builder()
                .taskId("1")
                .state(MLTaskState.CREATED)
                .build();
        MLTask task2 = MLTask.builder()
                .taskId("2")
                .state(MLTaskState.RUNNING)
                .build();
        MLTask task3 = MLTask.builder()
                .taskId("3")
                .state(MLTaskState.FAILED)
                .build();
        MLTask task4 = MLTask.builder()
                .taskId("4")
                .state(MLTaskState.COMPLETED)
                .build();
        mlTaskManager.add(task1);
        mlTaskManager.add(task2);
        mlTaskManager.add(task3);
        mlTaskManager.add(task4);
        Assert.assertEquals(mlTaskManager.getRunningTaskCount(), 1);
    }

    @Test
    public void testClear() {
        MLTask task1 = MLTask.builder()
                .taskId("1")
                .state(MLTaskState.CREATED)
                .build();
        MLTask task2 = MLTask.builder()
                .taskId("2")
                .state(MLTaskState.RUNNING)
                .build();
        MLTask task3 = MLTask.builder()
                .taskId("3")
                .state(MLTaskState.FAILED)
                .build();
        MLTask task4 = MLTask.builder()
                .taskId("4")
                .state(MLTaskState.COMPLETED)
                .build();
        mlTaskManager.add(task1);
        mlTaskManager.add(task2);
        mlTaskManager.add(task3);
        mlTaskManager.add(task4);
        mlTaskManager.clear();
        Assert.assertFalse(mlTaskManager.contains(task1.getTaskId()));
        Assert.assertFalse(mlTaskManager.contains(task2.getTaskId()));
        Assert.assertFalse(mlTaskManager.contains(task3.getTaskId()));
        Assert.assertFalse(mlTaskManager.contains(task4.getTaskId()));
    }

}
