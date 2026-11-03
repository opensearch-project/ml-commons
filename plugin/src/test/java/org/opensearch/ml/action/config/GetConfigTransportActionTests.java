/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.get.GetResult;
import org.opensearch.ml.common.Configuration;
import org.opensearch.ml.common.MLConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.config.MLConfigGetRequest;
import org.opensearch.ml.common.transport.config.MLConfigGetResponse;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.GetDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

public class GetConfigTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    SdkClient sdkClient;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<MLConfigGetResponse> actionListener;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetConfigTransportAction getConfigTransportAction;
    MLConfigGetRequest mlConfigGetRequest;
    ThreadContext threadContext;

    private static final String TEST_INDEX = "test-index";
    private static final String TEST_ID = "test-id";

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlConfigGetRequest = MLConfigGetRequest.builder().configId("test_id").build();

        getConfigTransportAction = spy(
            new GetConfigTransportAction(transportService, actionFilters, client, sdkClient, xContentRegistry, mlFeatureEnabledSetting)
        );

        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
    }

    public void testGetTask_NullResponse() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        GetDataObjectResponse response = mock(GetDataObjectResponse.class);
        GetResponse getResponse = new GetResponse(new GetResult(TEST_INDEX, TEST_ID, -2, 0, 1, false, null, null, null));
        when(response.getResponse()).thenReturn(getResponse);
        CompletionStage<GetDataObjectResponse> completionStageResult = CompletableFuture.completedStage(response);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(completionStageResult);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find config with the provided config id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_RuntimeException() {
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenThrow(new RuntimeException("errorMessage"));
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_IndexNotFoundException() {
        CompletionStage<GetDataObjectResponse> completionStageResult = mock(CompletionStage.class);
        doAnswer(invocationOnMock -> {
            BiConsumer<GetDataObjectResponse, Throwable> biConsumer = invocationOnMock.getArgument(0);
            biConsumer.accept(null, new IndexNotFoundException("Index Not Found"));
            return null;
        }).when(completionStageResult).whenComplete(any(BiConsumer.class));
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(completionStageResult);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get config index", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_generalException() {
        CompletionStage<GetDataObjectResponse> completionStageResult = mock(CompletionStage.class);
        doAnswer(invocationOnMock -> {
            BiConsumer<GetDataObjectResponse, Throwable> biConsumer = invocationOnMock.getArgument(0);
            biConsumer.accept(null, new RuntimeException("System error"));
            return null;
        }).when(completionStageResult).whenComplete(any(BiConsumer.class));
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(completionStageResult);
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("System error", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_Context_Exception() {
        String configId = "test-config-id";

        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest getRequest = new MLConfigGetRequest(configId, null);
        Task task = mock(Task.class);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException());
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(client).get(any(), any());
        try {
            getConfigTransportAction.doExecute(task, getRequest, actionListener);
        } catch (Exception e) {
            assertEquals(e.getClass(), RuntimeException.class);
        }
    }

    @Test
    public void testDoExecute_Success() throws IOException {
        String configID = "config_id";
        GetResponse getResponse = prepareMLConfig(configID);
        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest request = new MLConfigGetRequest(configID, null);
        Task task = mock(Task.class);

        GetDataObjectResponse response = mock(GetDataObjectResponse.class);
        when(response.getResponse()).thenReturn(getResponse);
        CompletionStage<GetDataObjectResponse> completionStageResult = CompletableFuture.completedStage(response);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(completionStageResult);
        getConfigTransportAction.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLConfigGetResponse.class));
    }

    @Test
    public void testDoExecute_Success_ForNewFields() throws IOException {
        String configID = "config_id";
        MLConfig mlConfig = new MLConfig(null, "olly_agent", null, new Configuration("agent_id"), Instant.EPOCH, null, Instant.EPOCH, null);

        XContentBuilder content = mlConfig.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", configID, 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest request = new MLConfigGetRequest(configID, null);
        Task task = mock(Task.class);

        GetDataObjectResponse response = mock(GetDataObjectResponse.class);
        when(response.getResponse()).thenReturn(getResponse);
        CompletionStage<GetDataObjectResponse> completionStageResult = CompletableFuture.completedStage(response);
        when(sdkClient.getDataObjectAsync(any(GetDataObjectRequest.class))).thenReturn(completionStageResult);

        getConfigTransportAction.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLConfigGetResponse.class));
    }

    public GetResponse prepareMLConfig(String configID) throws IOException {

        MLConfig mlConfig = new MLConfig("olly_agent", null, new Configuration("agent_id"), null, Instant.EPOCH, Instant.EPOCH, null, null);

        XContentBuilder content = mlConfig.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", configID, 111l, 111l, 111l, true, bytesReference, null, null);
        return new GetResponse(getResult);
    }

    @Test
    public void testDoExecute_Rejected_MASTER_KEY() throws IOException {
        String configID = MASTER_KEY;
        GetResponse getResponse = prepareMLConfig(configID);
        ActionListener<MLConfigGetResponse> actionListener = mock(ActionListener.class);
        MLConfigGetRequest request = new MLConfigGetRequest(configID, null);
        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        ArgumentCaptor<OpenSearchStatusException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);

        getConfigTransportAction.doExecute(task, request, actionListener);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals(RestStatus.FORBIDDEN, argumentCaptor.getValue().status());
        assertEquals("You are not allowed to access this config doc", argumentCaptor.getValue().getLocalizedMessage());

    }
}
