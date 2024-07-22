/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_THREAD_POOL_PREFIX;

import java.io.IOException;
import java.time.Instant;
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
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.ml.sdkclient.SdkClientFactory;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetConfigTransportActionTests extends OpenSearchTestCase {
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
    ActionListener<MLConfigGetResponse> actionListener;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetConfigTransportAction getConfigTransportAction;
    MLConfigGetRequest mlConfigGetRequest;
    ThreadContext threadContext;
    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        GetConfigTransportActionTests.class.getName(),
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
        Settings settings = Settings.builder().build();
        mlConfigGetRequest = MLConfigGetRequest.builder().configId("test_id").build();
        sdkClient = SdkClientFactory.createSdkClient(client, xContentRegistry, settings);
        getConfigTransportAction = spy(
            new GetConfigTransportAction(transportService, actionFilters, client, sdkClient, xContentRegistry, mlFeatureEnabledSetting)
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
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(null);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLConfigGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find config with the provided config id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_RuntimeException() throws InterruptedException {

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new RuntimeException("errorMessage"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLConfigGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_IndexNotFoundException() throws InterruptedException {
        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new IndexNotFoundException("Index Not Found"));
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLConfigGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get config index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_Context_Exception() {
        String configId = "test-config-id";

        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest getRequest = new MLConfigGetRequest(configId, null);
        Task task = mock(Task.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException());

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onFailure(new RuntimeException());
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        try {
            CountDownLatch latch = new CountDownLatch(1);
            LatchedActionListener<MLConfigGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
            getConfigTransportAction.doExecute(null, mlConfigGetRequest, latchedActionListener);
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            assertEquals(e.getClass(), RuntimeException.class);
        }
    }

    @Test
    public void testDoExecute_Success() throws IOException, InterruptedException {
        String configID = "config_id";
        GetResponse getResponse = prepareMLConfig(configID);
        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest request = new MLConfigGetRequest(configID, null);
        Task task = mock(Task.class);

        PlainActionFuture<GetResponse> getFuture = PlainActionFuture.newFuture();
        getFuture.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(getFuture);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLConfigGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);
        verify(actionListener).onResponse(any(MLConfigGetResponse.class));
    }

    public GetResponse prepareMLConfig(String configID) throws IOException {

        MLConfig mlConfig = new MLConfig("olly_agent", new Configuration("agent_id"), Instant.EPOCH, Instant.EPOCH, null);

        XContentBuilder content = mlConfig.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", configID, 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
