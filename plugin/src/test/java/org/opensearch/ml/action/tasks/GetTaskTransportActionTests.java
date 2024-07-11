/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.OpenSearchExecutors;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.transport.task.MLTaskGetRequest;
import org.opensearch.ml.common.transport.task.MLTaskGetResponse;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetTaskTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLTaskGetResponse> actionListener;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        GetTaskTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    GetTaskTransportAction getTaskTransportAction;
    MLTaskGetRequest mlTaskGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        Settings settings = Settings.builder().build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        mlTaskGetRequest = MLTaskGetRequest.builder().taskId("test_id").build();

        getTaskTransportAction = spy(
            new GetTaskTransportAction(transportService, actionFilters, client, sdkClient, xContentRegistry, mlFeatureEnabledSetting)
        );

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    public void testGetTask_NullResponse() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(null);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLTaskGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find task", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_RuntimeException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("errorMessage"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLTaskGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_IndexNotFoundException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IndexNotFoundException("Index Not Found"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLTaskGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getTaskTransportAction.doExecute(null, mlTaskGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find task", argumentCaptor.getValue().getMessage());
    }
}
