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

import java.io.IOException;
import java.util.Set;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.memory.MemoryTestUtil;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteConversationTransportActionTests extends OpenSearchTestCase {

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
    ActionListener<DeleteConversationResponse> actionListener;

    @Mock
    OpenSearchConversationalMemoryHandler cmHandler;

    DeleteConversationRequest request;
    DeleteConversationTransportAction action;
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
        ActionListener<DeleteConversationResponse> al = (ActionListener<DeleteConversationResponse>) Mockito.mock(ActionListener.class);
        this.actionListener = al;
        this.cmHandler = Mockito.mock(OpenSearchConversationalMemoryHandler.class);

        this.request = new DeleteConversationRequest("test");

        Settings settings = Settings.builder().put(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey(), true).build();
        this.threadContext = new ThreadContext(settings);
        when(this.client.threadPool()).thenReturn(this.threadPool);
        when(this.threadPool.getThreadContext()).thenReturn(this.threadContext);
        when(this.clusterService.getSettings()).thenReturn(settings);
        when(this.clusterService.getClusterSettings())
            .thenReturn(new ClusterSettings(settings, Set.of(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED)));

        this.action = spy(new DeleteConversationTransportAction(transportService, actionFilters, cmHandler, client, clusterService));
    }

    public void testDeleteConversation() {
        log.info("testing delete conversation transport");
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(cmHandler).deleteConversation(any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<DeleteConversationResponse> argCaptor = ArgumentCaptor.forClass(DeleteConversationResponse.class);
        verify(actionListener).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().wasSuccessful());
    }

    public void testDeleteConversation_NoAccess() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al
                .onFailure(
                    new OpenSearchStatusException("Resources not found. Failed to delete the memory for " + "test", RestStatus.NOT_FOUND)
                );
            return null;
        }).when(cmHandler).deleteConversation(any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<OpenSearchStatusException> argCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Resources not found. Failed to delete the memory for test"));
    }

    public void testDeleteFails_thenFail() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onFailure(new Exception("Test Fail Case"));
            return null;
        }).when(cmHandler).deleteConversation(any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test Fail Case"));
    }

    public void testdoExecuteFails_thenFail() {
        doThrow(new RuntimeException("Test doExecute Error")).when(cmHandler).deleteConversation(any(), any());
        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Test doExecute Error"));
    }

    public void testFeatureDisabled_ThenFail() {
        clusterService = MemoryTestUtil.clusterServiceWithMemoryFeatureDisabled();
        this.action = spy(new DeleteConversationTransportAction(transportService, actionFilters, cmHandler, client, clusterService));

        action.doExecute(null, request, actionListener);
        ArgumentCaptor<Exception> argCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(actionListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().startsWith("The experimental Conversation Memory feature is not enabled."));
    }

}
