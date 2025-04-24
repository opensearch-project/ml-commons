/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.action.conversation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.MemoryTestUtil;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetInteractionsTransportActionTests extends OpenSearchTestCase {

    @Mock
    ThreadPool threadPool;

    @Mock
    Client client;

    @Mock
    ClusterService clusterService;

    @Mock
    NamedXContentRegistry xContentRegistry;

    @Mock
    TransportService transportService;

    @Mock
    ActionFilters actionFilters;

    @Mock
    ActionListener<GetInteractionsResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    GetInteractionsRequest request;
    GetInteractionsTransportAction action;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        this.threadPool = Mockito.mock(ThreadPool.class);
        this.client = Mockito.mock(Client.class);
        this.clusterService = Mockito.mock(ClusterService.class);
        this.xContentRegistry = Mockito.mock(NamedXContentRegistry.class);
        this.transportService = Mockito.mock(TransportService.class);
        this.actionFilters = Mockito.mock(ActionFilters.class);
        @SuppressWarnings("unchecked")
        ActionListener<GetInteractionsResponse> al = (ActionListener<GetInteractionsResponse>) Mockito.mock(ActionListener.class);
        this.actionListener = al;
        this.cmHandler = Mockito.mock(OpenSearchConversationalMemoryHandler.class);

        this.request = new GetInteractionsRequest("test-cid");

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED)));

        this.action = spy(new GetInteractionsTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
    }

    public void testGetInteractions_noMorePages() {
        log.info("test get interactions transport");
        Interaction testInteraction = new Interaction(
            "test-iid",
            Instant.now(),
            "test-cid",
            "test-input",
            "pt",
            "test-response",
            "test-origin",
            Collections.singletonMap("metadata", "some meta")
        );
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onResponse(List.of(testInteraction));
            return null;
        }).when(cmHandler).getInteractions(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<GetInteractionsResponse> argCaptor = ArgumentCaptor.forClass(GetInteractionsResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        List<Interaction> interactions = argCaptor.getValue().getInteractions();
        assert (interactions.size() == 1);
        Interaction interaction = interactions.get(0);
        assert (interaction.equals(testInteraction));
        assert (!argCaptor.getValue().hasMorePages());
    }

    public void testGetInteractions_MorePages() {
        log.info("test get interactions transport");
        Interaction testInteraction = new Interaction(
            "test-iid",
            Instant.now(),
            "test-cid",
            "test-input",
            "pt",
            "test-response",
            "test-origin",
            Collections.singletonMap("metadata", "some meta")
        );
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onResponse(List.of(testInteraction));
            return null;
        }).when(cmHandler).getInteractions(any(), anyInt(), anyInt(), any());
        GetInteractionsRequest shortPageRequest = new GetInteractionsRequest("test-cid", 1);
        action.doExecute(null, shortPageRequest, actionListener);
        ArgumentCaptor<GetInteractionsResponse> argCaptor = ArgumentCaptor.forClass(GetInteractionsResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        List<Interaction> interactions = argCaptor.getValue().getInteractions();
        assert (interactions.size() == 1);
        Interaction interaction = interactions.get(0);
        assert (interaction.equals(testInteraction));
        assert (argCaptor.getValue().hasMorePages());
    }

    public void testGetInteractionsFails_thenFail() {
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Testing Failure"));
            return null;
        }).when(cmHandler).getInteractions(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Testing Failure"));
    }

    public void testDoExecuteFails_thenFail() {
        doThrow(new RuntimeException("Failure in doExecute")).when(cmHandler).getInteractions(any(), anyInt(), anyInt(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Failure in doExecute"));
    }

    public void testFeatureDisabled_ThenFail() {
        clusterService = MemoryTestUtil.clusterServiceWithMemoryFeatureDisabled();
        this.action = spy(new GetInteractionsTransportAction(transportService, actionFilters, cmHandler, client, clusterService));

        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assertEquals(argCaptor.getValue().getMessage(), ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE);
    }
}
