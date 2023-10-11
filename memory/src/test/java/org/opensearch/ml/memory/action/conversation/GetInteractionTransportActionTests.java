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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
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
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

public class GetInteractionTransportActionTests extends OpenSearchTestCase {
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
    ActionListener<GetInteractionResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    GetInteractionRequest request;
    GetInteractionTransportAction action;
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
        ActionListener<GetInteractionResponse> al = (ActionListener<GetInteractionResponse>) Mockito.mock(ActionListener.class);
        this.actionListener = al;
        this.cmHandler = Mockito.mock(OpenSearchConversationalMemoryHandler.class);

        this.request = new GetInteractionRequest("cid", "iid");

        Settings settings = Settings.builder().put(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED)));

        this.action = spy(new GetInteractionTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
    }

    public void testGetInteraction() {
        Interaction testInteraction = new Interaction(
            "iid",
            Instant.now(),
            "cid",
            "test-input",
            "pt",
            "test-response",
            "test-origin",
            Collections.singletonMap("meta", "some meta")
        );
        doAnswer(invocation -> {
            ActionListener<Interaction> listener = invocation.getArgument(2);
            listener.onResponse(testInteraction);
            return null;
        }).when(cmHandler).getInteraction(any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<GetInteractionResponse> argCaptor = ArgumentCaptor.forClass(GetInteractionResponse.class);
        verify(actionListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().getInteraction().getId().equals("iid"));
    }

    public void testGetInteractionFails_ThenFail() {
        doAnswer(invocation -> {
            ActionListener<Interaction> listener = invocation.getArgument(2);
            listener.onFailure(new Exception("Storage layer failure"));
            return null;
        }).when(cmHandler).getInteraction(any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Storage layer failure"));
    }

    public void testHandlerThrows_ThenFail() {
        doThrow(new RuntimeException("CMHandler Failure")).when(cmHandler).getInteraction(any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener, times(1)).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("CMHandler Failure"));
    }

    public void testFeatureDisabled_ThenFail() {
        when(this.clusterService.getSettings()).thenReturn(Settings.EMPTY);
        when(this.clusterService.getClusterSettings()).thenReturn(new ClusterSettings(Settings.EMPTY, Set.of(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED)));
        this.action = spy(new GetInteractionTransportAction(transportService, actionFilters, cmHandler, client, clusterService));

        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().startsWith("The experimental Conversation Memory feature is not enabled."));
    }
}
