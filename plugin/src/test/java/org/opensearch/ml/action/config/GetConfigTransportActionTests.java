/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.config;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.CommonValue.MASTER_KEY;

import java.io.IOException;
import java.time.Instant;

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
import org.opensearch.client.Client;
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
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetConfigTransportActionTests extends OpenSearchTestCase {
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

    @Mock
    ActionListener<MLConfigGetResponse> actionListener;

    @Mock
    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    GetConfigTransportAction getConfigTransportAction;
    MLConfigGetRequest mlConfigGetRequest;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        mlConfigGetRequest = MLConfigGetRequest.builder().configId("test_id").build();

        getConfigTransportAction = spy(
            new GetConfigTransportAction(transportService, actionFilters, client, xContentRegistry, mlFeatureEnabledSetting)
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
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find config with the provided config id: test_id", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_RuntimeException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("errorMessage"));
            return null;
        }).when(client).get(any(), any());
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("errorMessage", argumentCaptor.getValue().getMessage());
    }

    public void testGetTask_IndexNotFoundException() {
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new IndexNotFoundException("Index Not Found"));
            return null;
        }).when(client).get(any(), any());
        getConfigTransportAction.doExecute(null, mlConfigGetRequest, actionListener);
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

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

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

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

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
