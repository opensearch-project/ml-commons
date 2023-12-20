/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.OpenSearchException;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentResponse;
import org.opensearch.ml.engine.indices.MLIndicesHandler;
import org.opensearch.tasks.Task;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class RegisterAgentTransportActionTests extends OpenSearchTestCase {

    @Mock
    private Client client;

    @Mock
    private MLIndicesHandler mlIndicesHandler;

    @Mock
    private ActionFilters actionFilters;

    @Mock
    private TransportService transportService;

    @Mock
    private Task task;

    @Mock
    private ActionListener<MLRegisterAgentResponse> actionListener;

    @Mock
    private ThreadPool threadPool;

    private TransportRegisterAgentAction transportRegisterAgentAction;
    private Settings settings;
    private ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        settings = Settings.builder().build();
        threadContext = new ThreadContext(settings);

        threadContext.putTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT, "alex|IT,HR|engineering,operations");

        when(client.threadPool()).thenReturn(threadPool);
        when(threadPool.getThreadContext()).thenReturn(threadContext);
        transportRegisterAgentAction = new TransportRegisterAgentAction(transportService, actionFilters, client, mlIndicesHandler);
    }

    public void test_execute_registerAgent_success() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type("some type")
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            IndexResponse indexResponse = new IndexResponse(new ShardId("test", "test", 1), "test", 1l, 1l, 1l, true);
            al.onResponse(indexResponse);
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<MLRegisterAgentResponse> argumentCaptor = ArgumentCaptor.forClass(MLRegisterAgentResponse.class);
        verify(actionListener).onResponse(argumentCaptor.capture());
    }

    public void test_execute_registerAgent_AgentIndexNotInitialized() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type("some type")
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(false);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<OpenSearchException> argumentCaptor = ArgumentCaptor.forClass(OpenSearchException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("Failed to create ML agent index", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_registerAgent_IndexFailure() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type("some type")
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onResponse(true);
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        doAnswer(invocation -> {
            ActionListener<IndexResponse> al = invocation.getArgument(1);
            al.onFailure(new RuntimeException("index failure"));
            return null;
        }).when(client).index(any(), any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());

        assertEquals("index failure", argumentCaptor.getValue().getMessage());
    }

    public void test_execute_registerAgent_InitAgentIndexFailure() {
        MLRegisterAgentRequest request = mock(MLRegisterAgentRequest.class);
        MLAgent mlAgent = MLAgent
            .builder()
            .name("agent")
            .type("some type")
            .description("description")
            .llm(new LLMSpec("model_id", new HashMap<>()))
            .build();
        when(request.getMlAgent()).thenReturn(mlAgent);

        doAnswer(invocation -> {
            ActionListener<Boolean> listener = invocation.getArgument(0);
            listener.onFailure(new RuntimeException("agent index initialization failed"));
            return null;
        }).when(mlIndicesHandler).initMLAgentIndex(any());

        transportRegisterAgentAction.doExecute(task, request, actionListener);
        ArgumentCaptor<RuntimeException> argumentCaptor = ArgumentCaptor.forClass(RuntimeException.class);
        verify(actionListener).onFailure(argumentCaptor.capture());
        assertEquals("agent index initialization failed", argumentCaptor.getValue().getMessage());
    }
}
