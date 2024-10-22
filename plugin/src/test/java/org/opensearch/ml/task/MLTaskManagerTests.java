/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.task;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.ML_TASK_INDEX;
import static org.opensearch.ml.common.MLTask.STATE_FIELD;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.MLTaskType;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

import com.google.common.collect.ImmutableMap;

public class MLTaskManagerTests extends OpenSearchTestCase {
    MLTaskManager mlTaskManager;
    MLTask mlTask;
    Client client;
    private SdkClient sdkClient;
    ThreadPool threadPool;
    ExecutorService executorService;
    ThreadContext threadContext;
    MLIndicesHandler mlIndicesHandler;
    private NamedXContentRegistry xContentRegistry;
    IndexResponse indexResponse;

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        MLTaskManagerTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() {
        this.client = mock(Client.class);
        this.threadPool = mock(ThreadPool.class);
        this.executorService = mock(ExecutorService.class);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(executorService);
        when(threadPool.executor(any())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(executorService).execute(any(Runnable.class));
        xContentRegistry = NamedXContentRegistry.EMPTY;
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);

        this.mlIndicesHandler = mock(MLIndicesHandler.class);
        this.mlTaskManager = spy(new MLTaskManager(client, sdkClient, threadPool, mlIndicesHandler));
        this.mlTask = MLTask
            .builder()
            .taskId("task id")
            .taskType(MLTaskType.PREDICTION)
            .createTime(Instant.now())
            .state(MLTaskState.CREATED)
            .build();
        indexResponse = new IndexResponse(new ShardId(ML_TASK_INDEX, "_na_", 0), "AGENT_ID", 1, 0, 2, true);
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    public void testAdd() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Duplicate taskId");
        mlTaskManager.add(mlTask);
        Assert.assertTrue(mlTaskManager.contains(mlTask.getTaskId()));
        mlTaskManager.add(mlTask);
    }

    public void testUpdateMLTaskWithNullOrEmptyMap() throws InterruptedException {
        mlTaskManager.add(mlTask);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);
        mlTaskManager.updateMLTask(mlTask.getTaskId(), null, new HashMap<>(), latchedActionListener, 0, false);
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(client, never()).update(any(), any());
        verify(listener, times(1)).onFailure(any());

        CountDownLatch latch1 = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener1 = new LatchedActionListener<>(listener, latch1);
        mlTaskManager.updateMLTask(mlTask.getTaskId(), null, new HashMap<>(), latchedActionListener1, 0, false);
        latch1.await(500, TimeUnit.MILLISECONDS);
        verify(client, never()).update(any(), any());
        verify(listener, times(2)).onFailure(any());
    }

    public void testUpdateMLTask_NonExistingTask() throws InterruptedException {
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);
        mlTaskManager.updateMLTask(mlTask.getTaskId(), null, new HashMap<>(), latchedActionListener, 0, false);
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(client, never()).update(any(), any());
        verify(listener, times(1)).onFailure(argumentCaptor.capture());
        assertEquals("Can't find task in cache: task id", argumentCaptor.getValue().getMessage());
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
        mlTaskManager
            .updateMLTask(asyncMlTask.getTaskId(), null, ImmutableMap.of(MLTask.ERROR_FIELD, "test error"), ActionListener.wrap(r -> {
                ActionListener<UpdateResponse> listener = mock(ActionListener.class);
                mlTaskManager.updateMLTask(asyncMlTask.getTaskId(), null, new HashMap<>(), listener, 0, false);
                verify(client, times(1)).update(any(), any());
                verify(listener, times(1)).onFailure(argumentCaptor.capture());
                assertEquals("Other updating request not finished yet", argumentCaptor.getValue().getMessage());
            }, Assert::assertNull), 0, false);
    }

    public void testUpdateMLTask_FailedToUpdate() throws InterruptedException {
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        String errorMessage = "test error message";

        when(client.update(any(UpdateRequest.class))).thenThrow(new RuntimeException(errorMessage));

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);
        mlTaskManager
            .updateMLTask(
                asyncMlTask.getTaskId(),
                null,
                ImmutableMap.of(MLTask.ERROR_FIELD, "test error"),
                latchedActionListener,
                0,
                false
            );
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(listener, times(1)).onFailure(argumentCaptor.capture());
        assertEquals(errorMessage, argumentCaptor.getValue().getMessage());
    }

    public void testUpdateMLTask_ThrowException() throws InterruptedException {
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        String errorMessage = "test error message";
        when(client.update(any(UpdateRequest.class))).thenThrow(new RuntimeException(errorMessage));

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);
        mlTaskManager
            .updateMLTask(asyncMlTask.getTaskId(), null, ImmutableMap.of(MLTask.ERROR_FIELD, "test error"), latchedActionListener, 0, true);
        latch.await(500, TimeUnit.MILLISECONDS);
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

    public void testCreateMlTask_InitIndexReturnFalse() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<IndexResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);

        mlTaskManager.createMLTask(mlTask, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(listener).onFailure(any(RuntimeException.class));
    }

    public void testCreateMLTask_Success() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        PlainActionFuture<IndexResponse> future = PlainActionFuture.newFuture();
        future.onResponse(indexResponse);
        when(client.index(any(IndexRequest.class))).thenReturn(future);

        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<IndexResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);

        mlTaskManager.createMLTask(mlTask, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<IndexResponse> argumentCaptor = ArgumentCaptor.forClass(IndexResponse.class);

        verify(listener).onResponse(argumentCaptor.capture());
    }

    public void testCreateMLTask_FailureDuringIndexing() throws InterruptedException {
        // Simulate successful index creation
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        // Simulate task creation failure
        doAnswer(invocation -> {
            ActionListener<IndexResponse> actionListener = invocation.getArgument(1);
            actionListener.onFailure(new RuntimeException("Indexing error"));
            return null;
        }).when(client).index(any());

        ActionListener<IndexResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<IndexResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);

        mlTaskManager.createMLTask(mlTask, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        // Verify failure handling
        verify(listener).onFailure(any(RuntimeException.class));
    }

    public void testUpdateMLTask_SemaphoreReleaseOnException() throws InterruptedException {
        // Add the task
        MLTask asyncMlTask = mlTask.toBuilder().async(true).build();
        mlTaskManager.add(asyncMlTask);

        // Mock semaphore acquisition behavior
        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(asyncMlTask.getTaskId());
        Semaphore semaphore = spy(mlTaskCache.getUpdateTaskIndexSemaphore());
        doAnswer(invocation -> true).when(semaphore).tryAcquire(anyLong(), any(TimeUnit.class));

        // Simulate update exception
        doThrow(new RuntimeException("Update error")).when(client).update(any(), any());

        // Setup listener
        ActionListener<UpdateResponse> listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<UpdateResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);

        // Trigger the update
        mlTaskManager
            .updateMLTask(
                asyncMlTask.getTaskId(),
                null,
                ImmutableMap.of(STATE_FIELD, MLTaskState.RUNNING),
                latchedActionListener,
                0,
                false
            );
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(listener).onFailure(any(RuntimeException.class));
    }

    public void testCreateMlTask_IndexException() throws InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLTaskIndex(any(ActionListener.class));

        String errorMessage = "test error message";
        when(client.index(any(IndexRequest.class))).thenThrow(new RuntimeException(errorMessage));
        ActionListener listener = mock(ActionListener.class);
        CountDownLatch latch = new CountDownLatch(1);
        ActionListener<IndexResponse> latchedActionListener = new LatchedActionListener<>(listener, latch);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        mlTaskManager.createMLTask(mlTask, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
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

    public void testRemoveRunningTask() {
        MLTask runningTask = MLTask.builder().taskId("1").taskType(MLTaskType.PREDICTION).state(MLTaskState.RUNNING).build();
        mlTaskManager.add(runningTask);
        Assert.assertTrue(mlTaskManager.contains(runningTask.getTaskId()));

        // Ensure the running task count increases
        Assert.assertEquals(1, mlTaskManager.getRunningTaskCount());

        // Remove task and ensure running task count decrements
        mlTaskManager.remove(runningTask.getTaskId());
        Assert.assertEquals(0, mlTaskManager.getRunningTaskCount());
        Assert.assertFalse(mlTaskManager.contains(runningTask.getTaskId()));
    }

    public void testClearWithRunningTasks() {
        MLTask runningTask = MLTask.builder().taskId("1").taskType(MLTaskType.PREDICTION).state(MLTaskState.RUNNING).build();
        MLTask createdTask = MLTask.builder().taskId("2").taskType(MLTaskType.PREDICTION).state(MLTaskState.CREATED).build();

        mlTaskManager.add(runningTask);
        mlTaskManager.add(createdTask);

        // Ensure running task count is 1
        Assert.assertEquals(1, mlTaskManager.getRunningTaskCount());

        // Clear tasks and assert cleanup
        mlTaskManager.clear();
        Assert.assertFalse(mlTaskManager.contains(runningTask.getTaskId()));
        Assert.assertFalse(mlTaskManager.contains(createdTask.getTaskId()));
        Assert.assertEquals(0, mlTaskManager.getRunningTaskCount());
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
        MLTask task1 = MLTask.builder().taskId("1").taskType(MLTaskType.REGISTER_MODEL).state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").taskType(MLTaskType.REGISTER_MODEL).state(MLTaskState.CREATED).build();
        int limit = 1;
        mlTaskManager.checkLimitAndAddRunningTask(task1, limit);
        MLTask mlTask = mlTaskManager.getMLTask("1");
        assertEquals(MLTaskState.RUNNING, mlTask.getState());

        try {
            mlTaskManager.checkLimitAndAddRunningTask(task2, limit);
        } catch (Exception e) {
            assertEquals("exceed max running task limit", e.getMessage());
        }

        mlTaskManager.remove(task1.getTaskId());
        assertEquals(0, mlTaskManager.getRunningTaskCount());
        mlTaskManager.checkLimitAndAddRunningTask(task2, limit);
    }

    public void testCheckLimitAndAddRunningTaskNoLimit() {
        MLTask task1 = MLTask.builder().taskId("1").taskType(MLTaskType.PREDICTION).state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").taskType(MLTaskType.PREDICTION).state(MLTaskState.CREATED).build();

        // Set a high limit to simulate no practical limit
        int noLimit = Integer.MAX_VALUE;

        // No limit specified, we simulate this by passing a very high limit
        mlTaskManager.checkLimitAndAddRunningTask(task1, noLimit);
        mlTaskManager.checkLimitAndAddRunningTask(task2, noLimit);

        // Verify both tasks are in RUNNING state and running task count is 2
        Assert.assertEquals(2, mlTaskManager.getRunningTaskCount());
        Assert.assertEquals(MLTaskState.RUNNING, mlTaskManager.getMLTask("1").getState());
        Assert.assertEquals(MLTaskState.RUNNING, mlTaskManager.getMLTask("2").getState());
    }

    public void testGetRunningTaskCountWithMultipleStates() {
        MLTask task1 = MLTask.builder().taskId("1").state(MLTaskState.CREATED).build();
        MLTask task2 = MLTask.builder().taskId("2").state(MLTaskState.RUNNING).build();
        MLTask task3 = MLTask.builder().taskId("3").state(MLTaskState.FAILED).build();
        MLTask task4 = MLTask.builder().taskId("4").state(MLTaskState.COMPLETED).build();

        mlTaskManager.add(task1);
        mlTaskManager.add(task2);
        mlTaskManager.add(task3);
        mlTaskManager.add(task4);

        // Only task2 should count as running
        Assert.assertEquals(1, mlTaskManager.getRunningTaskCount());
    }

    public void testMLTaskCache() {
        MLTask task = MLTask.builder().taskId("1").taskType(MLTaskType.REGISTER_MODEL).state(MLTaskState.CREATED).build();
        String node1 = "node1_id";
        String node2 = "node2_id";
        mlTaskManager.add(task, Arrays.asList(node1, node2));
        MLTaskCache mlTaskCache = mlTaskManager.getMLTaskCache(task.getTaskId());
        assertNotNull(mlTaskCache);
        assertEquals(task, mlTaskCache.getMlTask());
        assertFalse(mlTaskCache.hasError());

        Set<String> workNodes = mlTaskManager.getWorkNodes(task.getTaskId());
        assertEquals(2, workNodes.size());
        assertTrue(workNodes.contains(node1));
        assertTrue(workNodes.contains(node2));

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
