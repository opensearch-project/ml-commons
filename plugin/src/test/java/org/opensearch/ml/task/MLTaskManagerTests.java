/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.ActionListener;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.index.Index;
import org.opensearch.index.shard.ShardId;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.indices.MLIndicesHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;

public class MLTaskManagerTests extends OpenSearchTestCase {
    MLTaskManager mlTaskManager;
    MLTask mlTask;
    Client client;
    ThreadPool threadPool;
    ThreadContext threadContext;
    MLIndicesHandler mlIndicesHandler;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.threadPool = mock(ThreadPool.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

        this.mlIndicesHandler = mock(MLIndicesHandler.class);
        this.mlTaskManager = spy(new MLTaskManager(client, mlIndicesHandler));
        this.mlTask = MLTask
            .builder()
            .taskId("task id")
            .taskType(MLTaskType.PREDICTION)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
    }

    public void testAdd() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Duplicate taskId");
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.add(mlTask);
    }

    public void testUpdateTaskState() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Task not found");
        mlTaskManager.add(mlTask);
        mlTaskManager.updateTaskState(mlTask.getTaskId(), MLTaskState.RUNNING, true);
        Assert.assertSame(mlTaskManager.getMLTask(mlTask.getTaskId()).getState(), MLTaskState.RUNNING);
        mlTaskManager.updateTaskState("not exist", MLTaskState.RUNNING, true);
    }

    public void testUpdateTaskError() {
        expectedEx.expect(RuntimeException.class);
        expectedEx.expectMessage("Task not found");
        mlTaskManager.add(mlTask);
        mlTaskManager.updateTaskError(mlTask.getTaskId(), "error message", true);
        Assert.assertEquals("error message", mlTaskManager.getMLTask(mlTask.getTaskId()).getError());
        mlTaskManager.updateTaskError("not exist", "error message", true);
    }

    public void testUpdateTaskStateAndError() {
        mlTaskManager.add(mlTask);
        String error = "error message";
        mlTaskManager.updateTaskStateAndError(mlTask.getTaskId(), MLTaskState.FAILED, error, true);
        ArgumentCaptor<Map> updatedFields = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Long> timeoutValue = ArgumentCaptor.forClass(Long.class);
        verify(mlTaskManager).updateMLTask(eq(mlTask.getTaskId()), updatedFields.capture(), timeoutValue.capture());
        Map map = updatedFields.getValue();
        Assert.assertEquals(2, map.size());
        Assert.assertEquals(MLTaskState.FAILED.name(), map.get("state"));
        Assert.assertEquals(error, map.get("error"));
        Long value = timeoutValue.getValue();
        Assert.assertEquals(0, value.longValue());
    }

    public void testUpdateTaskStateAndError_SyncTask() {
        mlTaskManager.add(mlTask);
        mlTaskManager.updateTaskStateAndError(mlTask.getTaskId(), MLTaskState.FAILED, "test error", false);
        verify(mlTaskManager, never()).updateMLTask(eq(mlTask.getTaskId()), any(), anyLong());
    }

    public void testUpdateMLTaskWithNullOrEmptyMap() {
        mlTaskManager.add(mlTask);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        mlTaskManager.updateMLTask(mlTask.getTaskId(), null, listener, 0);
        verify(client, never()).update(any(), any());
        verify(listener, times(1)).onFailure(any());

        mlTaskManager.updateMLTask(mlTask.getTaskId(), new HashMap<>(), listener, 0);
        verify(client, never()).update(any(), any());
        verify(listener, times(2)).onFailure(any());
    }

    public void testUpdateMLTask_NonExistingTask() {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        mlTaskManager.updateMLTask(mlTask.getTaskId(), null, listener, 0);
        verify(client, never()).update(any(), any());
        verify(listener, times(1)).onFailure(argumentCaptor.capture());
        assertEquals("Can't find task", argumentCaptor.getValue().getMessage());
    }

    public void testUpdateMLTask_NoSemaphore() {
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(1);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            UpdateResponse output = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.CREATED);
            actionListener.onResponse(output);
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        mlTaskManager.updateMLTask(asyncMlTask.getTaskId(), ImmutableMap.of(MLTask.ERROR_FIELD, "test error"), ActionListener.wrap(r -> {
            ActionListener<UpdateResponse> listener = mock(ActionListener.class);
            mlTaskManager.updateMLTask(asyncMlTask.getTaskId(), null, listener, 0);
            verify(client, times(1)).update(any(), any());
            verify(listener, times(1)).onFailure(argumentCaptor.capture());
            assertEquals("Other updating request not finished yet", argumentCaptor.getValue().getMessage());
        }, e -> { assertNull(e); }), 0);
    }

    public void testUpdateMLTask_FailedToUpdate() {
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        String errorMessage = "test error message";
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException(errorMessage));
            return null;
        }).when(client).update(any(UpdateRequest.class), any());

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        mlTaskManager.updateMLTask(asyncMlTask.getTaskId(), ImmutableMap.of(MLTask.ERROR_FIELD, "test error"), listener, 0);
        verify(client, times(1)).update(any(), any());
        verify(listener, times(1)).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testUpdateMLTask_ThrowException() {
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        String errorMessage = "test error message";
        doThrow(new RuntimeException(errorMessage)).when(client).update(any(UpdateRequest.class), any());

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        mlTaskManager.updateMLTask(asyncMlTask.getTaskId(), ImmutableMap.of(MLTask.ERROR_FIELD, "test error"), listener, 0);
        verify(client, times(1)).update(any(), any());
        verify(listener, times(1)).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testRemove() {
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.remove(mlTask.getTaskId());
        Assert.assertFalse(mlTaskManager.contains(mlTask.getTaskId()));
    }

    public void testRemove_NonExistingTask() {
        Assert.assertFalse(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.remove(mlTask.getTaskId());
        Assert.assertFalse(mlTaskManager.contains(mlTask.getTaskId()));
    }

    public void testGetTask() {
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        MLTask task = mlTaskManager.getMLTask(this.mlTask.getTaskId());
        Assert.assertEquals(mlTask, task);
    }

    public void testGetTask_NonExisting() {
        Assert.assertFalse(mlTaskManager.contains(mlTask.getTaskId()));
        MLTask task = mlTaskManager.getMLTask(this.mlTask.getTaskId());
        Assert.assertNull(task);
    }

    public void testGetRunningTaskCount() {
        MLTask task1 = MLTask.builder().taskId("1").state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").state(MLTaskState.RUNNING).build();
        MLTask task3 = MLTask.builder().taskId("3").state(MLTaskState.FAILED).build();
        MLTask task4 = MLTask.builder().taskId("4").state(MLTaskState.COMPLETED).build();
        MLTask task5 = MLTask.builder().taskId("5").state(null).build();
        mlTaskManager.add(task1);
        mlTaskManager.add(task2);
        mlTaskManager.add(task3);
        mlTaskManager.add(task4);
        mlTaskManager.add(task5);
        Assert.assertEquals(1, mlTaskManager.getRunningTaskCount());
    }

    public void testClear() {
        MLTask task1 = MLTask.builder().taskId("1").state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").state(MLTaskState.RUNNING).build();
        MLTask task3 = MLTask.builder().taskId("3").state(MLTaskState.FAILED).build();
        MLTask task4 = MLTask.builder().taskId("4").state(MLTaskState.COMPLETED).build();
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

    public void testCreateMlTask_InitIndexReturnFalse() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        ActionListener listener = mock(ActionListener.class);
        mlTaskManager.createMLTask(mlTask, listener);
        verify(listener).onFailure(any());
    }

    public void testCreateMlTask_IndexException() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        String errorMessage = "test error message";
        doThrow(new RuntimeException(errorMessage)).when(client).index(any(), any());
        ActionListener listener = mock(ActionListener.class);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        mlTaskManager.createMLTask(mlTask, listener);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testCreateMlTask_FailToGetThreadPool() {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        String errorMessage = "test error message";
        doThrow(new RuntimeException(errorMessage)).when(threadPool).getThreadContext();
        ActionListener listener = mock(ActionListener.class);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        mlTaskManager.createMLTask(mlTask, listener);
        verify(listener).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testGetAllTaskIds() {
        MLTask task1 = MLTask.builder().taskId("1").state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").state(MLTaskState.CREATED).build();
        mlTaskManager.add(task1);
        mlTaskManager.add(task2);

        String[] taskIds = mlTaskManager.getAllTaskIds();
        assertEquals(taskIds.length, 2);
        assertNotEquals(taskIds, mlTaskManager.getAllTaskIds());
    }

    public void testCheckLimitAndAddRunningTask() {
        MLTask task1 = MLTask.builder().taskId("1").taskType(MLTaskType.UPLOAD_MODEL).state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").taskType(MLTaskType.UPLOAD_MODEL).state(MLTaskState.CREATED).build();
        int limit = 1;
        String error1 = mlTaskManager.checkLimitAndAddRunningTask(task1, limit);
        MLTask mlTask = mlTaskManager.getMLTask("1");
        assertEquals(MLTaskState.RUNNING, mlTask.getState());
        assertNull(error1);

        String error2 = mlTaskManager.checkLimitAndAddRunningTask(task2, limit);
        assertEquals("exceed max running task limit", error2);

        mlTaskManager.remove(task1.getTaskId());
        assertEquals(0, mlTaskManager.getRunningTaskCount());
        assertNull(mlTaskManager.checkLimitAndAddRunningTask(task2, limit));
    }

    public void testMLTaskCache() {
        MLTask task = MLTask.builder().taskId("1").taskType(MLTaskType.UPLOAD_MODEL).state(MLTaskState.CREATED).build();
        String node1 = "node1_id";
        String node2 = "node2_id";
        mlTaskManager.add(task, Arrays.asList(node1, node2));
        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(task.getTaskId());
        assertNotNull(mlTaskCache);
        assertEquals(task, mlTaskCache.getMlTask());
        assertFalse(mlTaskCache.hasError());

        List<String> workNodes = mlTaskManager.getWorkNodes(task.getTaskId());
        assertEquals(2, workNodes.size());
        assertEquals(node1, workNodes.get(0));
        assertEquals(node2, workNodes.get(1));

        String wrongTaskId = "wrong_task_id";
        assertNull(mlTaskManager.getWorkNodes(wrongTaskId));

        String error = "error_message1";
        mlTaskManager.addNodeError(task.getTaskId(), node1, error);
        mlTaskManager.addNodeError(wrongTaskId, node1, error);

        assertNull(mlTaskManager.getMLTaskCache(wrongTaskId));
        assertTrue(mlTaskCache.hasError());
        assertFalse(mlTaskCache.allNodeFailed());
        assertEquals("{node1_id=error_message1}", mlTaskManager.getMLTaskCache(task.getTaskId()).getErrors().toString());

        mlTaskManager.addNodeError(task.getTaskId(), node2, error);
        assertTrue(mlTaskCache.allNodeFailed());
    }
}
