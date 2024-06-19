/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.action.DocWriteResponse.Result.DELETED;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.replication.ReplicationResponse;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.MLTask;
import org.opensearch.ml.common.MLTaskState;
import org.opensearch.ml.common.exception.MLResourceNotFoundException;
import org.opensearch.ml.common.transport.task.MLTaskDeleteRequest;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class DeleteTaskTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    SdkClient sdkClient;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<DeleteResponse> actionListener;

    @Mock
    DeleteResponse deleteResponse;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    DeleteTaskTransportAction deleteTaskTransportAction;
    MLTaskDeleteRequest mlTaskDeleteRequest;
    ThreadContext threadContext;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        DeleteTaskTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        mlTaskDeleteRequest = MLTaskDeleteRequest.builder().taskId("test_id").build();
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);
        deleteTaskTransportAction = spy(
            new DeleteTaskTransportAction(transportService, actionFilters, client, sdkClient, xContentRegistry, mlFeatureEnabledSetting)
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));

        when(deleteResponse.getId()).thenReturn("TASK_ID");
        when(deleteResponse.getShardId()).thenReturn(mock(ShardId.class));
        when(deleteResponse.getShardInfo()).thenReturn(mock(ReplicationResponse.ShardInfo.class));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testDeleteTask_Success() throws IOException, InterruptedException {
        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onResponse(deleteResponse);
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        GetResponse getResponse = prepareMLTask(MLTaskState.COMPLETED);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<DeleteResponse> captor = ArgumentCaptor.forClass(DeleteResponse.class);
        verify(actionListener).onResponse(captor.capture());
        assertEquals("TASK_ID", captor.getValue().getId());
        assertEquals(DELETED, captor.getValue().getResult());
    }

    @Test
    public void testDeleteTask_CheckTaskState() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLTask(MLTaskState.RUNNING);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Task cannot be deleted in running state. Try after sometime", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_ResourceNotFoundException() throws IOException, InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new MLResourceNotFoundException("Fail to find task"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_GetResponseFailure() throws InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new Exception("errorMessage"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_GetResponseNullException() throws InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(null);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_RuntimeException() throws IOException, InterruptedException {

        when(deleteResponse.getResult()).thenReturn(DELETED);
        PlainActionFuture<DeleteResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("errorMessage"));
        when(client.delete(any(DeleteRequest.class))).thenReturn(future);

        GetResponse getResponse = prepareMLTask(MLTaskState.COMPLETED);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_ThreadContextError() throws InterruptedException {
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException("thread context error"));

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("thread context error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_IndexNotFoundException() throws InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new IndexNotFoundException("indexName"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertTrue(argumentCaptor.getValue() instanceof OpenSearchStatusException);
        assertEquals("Failed to find task", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_UncaughtException() throws InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new RuntimeException("uncaught exception"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("uncaught exception", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDeleteTask_InvalidTenantId() throws InterruptedException {

        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(true);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<DeleteResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        deleteTaskTransportAction.doExecute(null, mlTaskDeleteRequest, latchedActionListener);
        latch.await();

        verify(actionListener).onFailure(any(Exception.class));
    }

    public GetResponse prepareMLTask(MLTaskState mlTaskState) throws IOException {
        MLTask mlTask = MLTask.builder().taskId("taskID").state(mlTaskState).build();
        XContentBuilder content = mlTask.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111L, 111L, 111L, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }
}
