/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetTracesTransportActionTests extends OpenSearchTestCase {
    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<GetTracesResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    GetTracesRequest request;
    GetTracesTransportAction action;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);

        @SuppressWarnings("unchecked")
        ActionListener<GetTracesResponse> al = (ActionListener<GetTracesResponse>) Mockito.mock(ActionListener.class);
        this.actionListener = al;
        this.cmHandler = Mockito.mock(OpenSearchConversationalMemoryHandler.class);

        this.request = new GetTracesRequest("test-iid");

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);

        this.action = spy(new GetTracesTransportAction(transportService, actionFilters, cmHandler, client));
    }

    public void testGetTraces_noMorePages() {
        Interaction testTrace = new Interaction(
            "test-trace",
            Instant.now(),
            Instant.now(),
            "test-cid",
            "test-input",
            "pt",
            "test-response",
            "test-origin",
            Collections.singletonMap("metadata", "some meta"),
            "parent-id",
            1
        );
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onResponse(List.of(testTrace));
            return null;
        }).when(cmHandler).getTraces(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<GetTracesResponse> argCaptor = ArgumentCaptor.forClass(GetTracesResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        List<Interaction> traces = argCaptor.getValue().getTraces();
        assert (traces.size() == 1);
        Interaction trace = traces.get(0);
        assert (trace.equals(testTrace));
        assert (!argCaptor.getValue().hasMorePages());
    }

    public void testGetTraces_MorePages() {
        Interaction testTrace = new Interaction(
            "test-trace",
            Instant.now(),
            Instant.now(),
            "test-cid",
            "test-input",
            "pt",
            "test-response",
            "test-origin",
            Collections.singletonMap("metadata", "some meta"),
            "parent_id",
            1
        );
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onResponse(List.of(testTrace));
            return null;
        }).when(cmHandler).getTraces(any(), anyInt(), anyInt(), any());
        GetTracesRequest shortPageRequest = new GetTracesRequest("test-trace", 1);
        action.doExecute(null, shortPageRequest, actionListener);
        ArgumentCaptor<GetTracesResponse> argCaptor = ArgumentCaptor.forClass(GetTracesResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        List<Interaction> traces = argCaptor.getValue().getTraces();
        assert (traces.size() == 1);
        Interaction trace = traces.get(0);
        assert (trace.equals(testTrace));
        assert (argCaptor.getValue().hasMorePages());
    }

    public void testGetTracesFails_thenFail() {
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Testing Failure"));
            return null;
        }).when(cmHandler).getTraces(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Testing Failure"));
    }

    public void testDoExecuteFails_thenFail() {
        doThrow(new RuntimeException("Failure in doExecute")).when(cmHandler).getTraces(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in doExecute"));
    }
}
