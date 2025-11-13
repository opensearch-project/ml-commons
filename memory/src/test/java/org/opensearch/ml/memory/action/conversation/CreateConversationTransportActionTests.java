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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MEMORY_REST_ACCESS_RESTRICTED_BACKEND_ROLES;

import java.io.IOException;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.MemoryTestUtil;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CreateConversationTransportActionTests extends OpenSearchTestCase {

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
    ActionListener<CreateConversationResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    CreateConversationRequest request;
    CreateConversationTransportAction action;
    ThreadContext threadContext;

    @Before
    public void setup() throws IOException {
        MockitoAnnotations.openMocks(this);
        this.threadPool = Mockito.mock(ThreadPool.class);
        this.client = Mockito.mock(Client.class);
        this.clusterService = Mockito.mock(ClusterService.class);
        this.xContentRegistry = Mockito.mock(NamedXContentRegistry.class);
        this.transportService = Mockito.mock(TransportService.class);
        this.actionFilters = Mockito.mock(ActionFilters.class);
        @SuppressWarnings("unchecked")
        ActionListener<CreateConversationResponse> al = (ActionListener<CreateConversationResponse>) Mockito.mock(ActionListener.class);
        this.actionListener = al;
        this.cmHandler = Mockito.mock(OpenSearchConversationalMemoryHandler.class);

        this.request = new CreateConversationRequest("test");

        Settings settings = Settings.builder().put(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED, ML_COMMONS_MEMORY_REST_ACCESS_RESTRICTED_BACKEND_ROLES)));

        this.action = spy(new CreateConversationTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
    }

    public void testCreateConversation() {
        log.info("testing create conversation transport");
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(3);
            listener.onResponse("testID");
            return null;
        }).when(cmHandler).createConversation(any(), any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<CreateConversationResponse> argCaptor = ArgumentCaptor.forClass(CreateConversationResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().getId().equals("testID"));
    }

    public void testCreateConversationWithNullName() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(0);
            listener.onResponse("testID-2");
            return null;
        }).when(cmHandler).createConversation(any(ActionListener.class));
        String nullstr = null;
        this.request = new CreateConversationRequest(nullstr);
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<CreateConversationResponse> argCaptor = ArgumentCaptor.forClass(CreateConversationResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().getId().equals("testID-2"));
    }

    public void testCreateConversationFails_thenFail() {
        doAnswer(invocation -> {
            ActionListener<String> listener = invocation.getArgument(3);
            listener.onFailure(new Exception("Testing Error"));
            return null;
        }).when(cmHandler).createConversation(any(), any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Testing Error"));
    }

    public void testDoExecuteFails_thenFail() {
        doThrow(new RuntimeException("Test doExecute Error")).when(cmHandler).createConversation(any(), any(), any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test doExecute Error"));
    }

    public void testFeatureDisabled_ThenFail() {
        clusterService = MemoryTestUtil.clusterServiceWithMemoryFeatureDisabled();
        this.action = spy(new CreateConversationTransportAction(transportService, actionFilters, cmHandler, client, clusterService));

        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assertEquals(argCaptor.getValue().getMessage(), ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE);
    }

}
