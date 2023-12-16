/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.agent.MLMemorySpec;
import org.opensearch.ml.common.agent.MLToolSpec;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetAgentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;
    @Mock
    ThreadPool threadPool;
    @Mock
    private NamedXContentRegistry xContentRegistry;

    @Mock
    private TransportService transportService;

    @Mock
    private ActionFilters actionFilters;

    @InjectMocks
    private GetAgentTransportAction getAgentTransportAction;

    ThreadContext threadContext;
    MLAgent mlAgent;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        getAgentTransportAction = new GetAgentTransportAction(transportService, actionFilters, client, xContentRegistry);
        Settings settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);

    }

    @Test
    public void testDoExecute_Failure_Get_Agent() {
        String agentId = "test-agent-id-no-existed";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new Exception("Failed to get ML agent " + agentId);

        doAnswer(invocation -> {
            ActionListener<MLAgentGetResponse> listener = invocation.getArgument(1);
            listener.onFailure(exceptionToThrow);
            return null;
        }).when(client).get(any(), any());

        getAgentTransportAction.doExecute(task, getRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get ML agent " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_IndexNotFound() {
        String agentId = "test-agent-id-IndexNotFound";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new IndexNotFoundException("Failed to get agent index " + agentId);

        doAnswer(invocation -> {
            ActionListener<MLAgentGetResponse> listener = invocation.getArgument(1);
            listener.onFailure(exceptionToThrow);
            return null;
        }).when(client).get(any(), any());

        getAgentTransportAction.doExecute(task, getRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get agent index", argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_OpenSearchStatus() throws IOException {
        String agentId = "test-agent-id-OpenSearchStatus";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);

        Task task = mock(Task.class);

        Exception exceptionToThrow = new OpenSearchStatusException(
            "Failed to find agent with the provided agent id: " + agentId,
            RestStatus.NOT_FOUND
        );

        doAnswer(invocation -> {
            ActionListener<MLAgentGetResponse> listener = invocation.getArgument(1);
            listener.onFailure(exceptionToThrow);
            return null;
        }).when(client).get(any(), any());

        getAgentTransportAction.doExecute(task, getRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find agent with the provided agent id: " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_RuntimeException() {
        String agentId = "test-agent-id-RuntimeException";
        Task task = mock(Task.class);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);

        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException("Failed to get ML agent " + agentId));
            return null;
        }).when(client).get(any(), any());
        getAgentTransportAction.doExecute(task, getRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to get ML agent " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testGetTask_NullResponse() {
        String agentId = "test-agent-id-NullResponse";
        Task task = mock(Task.class);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(null);
            return null;
        }).when(client).get(any(), any());
        getAgentTransportAction.doExecute(task, getRequest, actionListener);
        ArgumentCaptor<Exception> argumentCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to find agent with the provided agent id: " + agentId, argumentCaptor.getValue().getMessage());
    }

    @Test
    public void testDoExecute_Failure_Context_Exception() {
        String agentId = "test-agent-id";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest getRequest = new MLAgentGetRequest(agentId);
        Task task = mock(Task.class);
        GetAgentTransportAction getAgentTransportActionNullContext = new GetAgentTransportAction(
            transportService,
            actionFilters,
            client,
            xContentRegistry
        );
        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenThrow(new RuntimeException());
        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onFailure(new RuntimeException());
            return null;
        }).when(client).get(any(), any());
        try {
            getAgentTransportActionNullContext.doExecute(task, getRequest, actionListener);
        } catch (Exception e) {
            assertEquals(e.getClass(), RuntimeException.class);
        }
    }

    @Test
    public void testDoExecute_NoAgentId() throws IOException {
        GetResponse getResponse = prepareMLAgent(null);
        String agentId = "test-agent-id";

        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest request = new MLAgentGetRequest(agentId);
        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        try {
            getAgentTransportAction.doExecute(task, request, actionListener);
        } catch (Exception e) {
            assertEquals(e.getClass(), IllegalArgumentException.class);
        }
    }

    @Test
    public void testDoExecute_Success() throws IOException {

        String agentId = "test-agent-id";
        GetResponse getResponse = prepareMLAgent(agentId);
        ActionListener<MLAgentGetResponse> actionListener = mock(ActionListener.class);
        MLAgentGetRequest request = new MLAgentGetRequest(agentId);
        Task task = mock(Task.class);

        doAnswer(invocation -> {
            ActionListener<GetResponse> listener = invocation.getArgument(1);
            listener.onResponse(getResponse);
            return null;
        }).when(client).get(any(), any());

        getAgentTransportAction.doExecute(task, request, actionListener);
        verify(actionListener).onResponse(any(MLAgentGetResponse.class));
    }

    public GetResponse prepareMLAgent(String agentId) throws IOException {

        mlAgent = new MLAgent(
            "test",
            "test",
            "test",
            new LLMSpec("test_model", Map.of("test_key", "test_value")),
            List.of(new MLToolSpec("test", "test", "test", Collections.EMPTY_MAP, false)),
            Map.of("test", "test"),
            new MLMemorySpec("test", "123", 0),
            Instant.EPOCH,
            Instant.EPOCH,
            "test"
        );

        XContentBuilder content = mlAgent.toXContent(XContentFactory.jsonBuilder(), ToXContent.EMPTY_PARAMS);
        BytesReference bytesReference = BytesReference.bytes(content);
        GetResult getResult = new GetResult("indexName", agentId, 111l, 111l, 111l, true, bytesReference, null, null);
        GetResponse getResponse = new GetResponse(getResult);
        return getResponse;
    }
}
