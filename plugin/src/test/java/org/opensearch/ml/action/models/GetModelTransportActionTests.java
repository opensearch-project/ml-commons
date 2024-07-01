/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.models;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
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
import org.opensearch.action.LatchedActionListener;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
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
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.transport.model.MLModelGetRequest;
import org.opensearch.ml.common.transport.model.MLModelGetResponse;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.sdkclient.LocalClusterIndicesClient;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.sdk.SdkClient;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ScalingExecutorBuilder;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetModelTransportActionTests extends OpenSearchTestCase {

    private static final TestThreadPool testThreadPool = new TestThreadPool(
        GetModelTransportActionTests.class.getName(),
        new ScalingExecutorBuilder(
            GENERAL_THREAD_POOL,
            1,
            Math.max(1, OpenSearchExecutors.allocatedProcessors(Settings.EMPTY) - 1),
            TimeValue.timeValueMinutes(1),
            ML_THREAD_POOL_PREFIX + GENERAL_THREAD_POOL
        )
    );

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    SdkClient sdkClient;

    @Mock
    ActionListener<MLModelGetResponse> actionListener;

    @Mock
    ClusterService clusterService;

    private Settings settings;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetModelTransportAction getModelTransportAction;
    MLModelGetRequest mlModelGetRequest;
    ThreadContext threadContext;

    @Mock
    private ModelAccessControlHelper modelAccessControlHelper;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlModelGetRequest = MLModelGetRequest.builder().modelId("test_id").build();
        settings = Settings.builder().build();
        sdkClient = new LocalClusterIndicesClient(client, xContentRegistry);
        when(mlFeatureEnabledSetting.isMultiTenancyEnabled()).thenReturn(false);

        getModelTransportAction = spy(
            new GetModelTransportAction(
                transportService,
                actionFilters,
                client,
                sdkClient,
                settings,
                xContentRegistry,
                clusterService,
                modelAccessControlHelper,
                mlFeatureEnabledSetting
            )
        );

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(true);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(clusterService.getSettings()).thenReturn(settings);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        when(threadPool.executor(anyString())).thenReturn(testThreadPool.executor(GENERAL_THREAD_POOL));
    }

    @AfterClass
    public static void cleanup() {
        ThreadPool.terminate(testThreadPool, 500, TimeUnit.MILLISECONDS);
    }

    public void testGetModel_UserHasNodeAccess() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onResponse(false);
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        GetResponse getResponse = prepareMLModel(false);
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_Success() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(false);
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onResponse(any(MLModelGetResponse.class));
    }

    public void testGetModelHidden_Success() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(true);
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        doReturn(true).when(getModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        verify(actionListener).onResponse(any(MLModelGetResponse.class));
    }

    public void testGetModelHidden_SuperUserPermissionError() throws IOException, InterruptedException {
        GetResponse getResponse = prepareMLModel(true);
        mlModelGetRequest = MLModelGetRequest.builder().modelId("test_id").isUserInitiatedGetRequest(true).build();
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        doReturn(false).when(getModelTransportAction).isSuperAdminUserWrapper(clusterService, client);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("User doesn't have privilege to perform this operation on this model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_ValidateAccessFailed() throws IOException, InterruptedException {
        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Failed to validate access"));
            return null;
        }).when(modelAccessControlHelper).validateModelGroupAccess(any(), any(), any(), any());

        GetResponse getResponse = prepareMLModel(false);
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(getResponse);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to validate access", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_NullResponse() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onResponse(null);
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find model with the provided model id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_IndexNotFoundException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new IndexNotFoundException("Fail to find model"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Fail to find model", argumentCaptor.getValue().getMessage());
    }

    public void testGetModel_RuntimeException() throws InterruptedException {
        PlainActionFuture<GetResponse> future = PlainActionFuture.newFuture();
        future.onFailure(new RuntimeException("errorMessage"));
        when(client.get(any(GetRequest.class))).thenReturn(future);

        CountDownLatch latch = new CountDownLatch(1);
        LatchedActionListener<MLModelGetResponse> latchedActionListener = new LatchedActionListener<>(actionListener, latch);
        getModelTransportAction.doExecute(null, mlModelGetRequest, latchedActionListener);
        latch.await(500, TimeUnit.MILLISECONDS);

        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public GetResponse prepareMLModel(boolean isHidden) throws IOException {
        MLModel mlModel = MLModel
            .builder()
            .modelId("test_id")
            .modelState(MLModelState.REGISTERED)
            .algorithm(FunctionName.TEXT_EMBEDDING)
            .isHidden(isHidden)
            .build();
        XContentBuilder content = mlModel.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", "111", 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
