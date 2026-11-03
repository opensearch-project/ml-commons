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
package org.opensearch.ml.memory.index;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.conversation.Interaction.InteractionBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class OpenSearchConversationalMemoryHandlerTests extends OpenSearchTestCase {

    @Mock
    ConversationMetaIndex conversationMetaIndex;

    @Mock
    InteractionsIndex interactionsIndex;

    OpenSearchConversationalMemoryHandler cmHandler;

    @Before
    public void setup() {
        conversationMetaIndex = mock(ConversationMetaIndex.class);
        interactionsIndex = mock(InteractionsIndex.class);
        cmHandler = new OpenSearchConversationalMemoryHandler(conversationMetaIndex, interactionsIndex);
    }

    public void testCreateConversation_NoName_FutureSuccess() {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(0);
            al.onResponse("cid");
            return null;
        }).when(conversationMetaIndex).createConversation(any());
        ActionFuture<String> result = cmHandler.createConversation();
        assert (result.actionGet(200).equals("cid"));
    }

    public void testCreateConversation_Named_FutureSuccess() {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(1);
            al.onResponse("cid");
            return null;
        }).when(conversationMetaIndex).createConversation(anyString(), any());
        ActionFuture<String> result = cmHandler.createConversation("FutureSuccess");
        assert (result.actionGet(200).equals("cid"));
    }

    public void testCreateConversation_AdditionalInfo_Success() throws Exception {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(3);
            al.onResponse("cid");
            return null;
        }).when(conversationMetaIndex).createConversation(anyString(), anyString(), any(), any());
        CompletableFuture<String> future = new CompletableFuture<>();
        cmHandler.createConversation("FutureSuccess", "", Map.of(), ActionListener.wrap(future::complete, future::completeExceptionally));
        assert (future.get(200, TimeUnit.MILLISECONDS).equals("cid"));
    }

    public void testCreateInteraction_Future() {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(7);
            al.onResponse("iid");
            return null;
        }).when(interactionsIndex).createInteraction(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        ActionFuture<String> result = cmHandler
            .createInteraction("cid", "inp", "pt", "rsp", "ogn", Collections.singletonMap("meta", "some meta"));
        assert (result.actionGet(200).equals("iid"));
    }

    public void testCreateInteraction_FromBuilder_Success() {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(7);
            al.onResponse("iid");
            return null;
        }).when(interactionsIndex).createInteraction(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        InteractionBuilder builder = Interaction
            .builder()
            .conversationId("cid")
            .input("inp")
            .origin("origin")
            .response("rsp")
            .promptTemplate("pt")
            .additionalInfo(Collections.singletonMap("meta", "some meta"));
        @SuppressWarnings("unchecked")
        ActionListener<String> createInteractionListener = mock(ActionListener.class);
        cmHandler.createInteraction(builder, createInteractionListener);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(createInteractionListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().equals("iid"));
    }

    public void testCreateInteraction_FromBuilder_Future() {
        doAnswer(invocation -> {
            ActionListener<String> al = invocation.getArgument(7);
            al.onResponse("iid");
            return null;
        }).when(interactionsIndex).createInteraction(anyString(), anyString(), anyString(), anyString(), anyString(), any(), any(), any());
        InteractionBuilder builder = Interaction
            .builder()
            .origin("ogn")
            .conversationId("cid")
            .input("inp")
            .response("rsp")
            .promptTemplate("pt")
            .additionalInfo(Collections.singletonMap("meta", "some meta"));
        ActionFuture<String> result = cmHandler.createInteraction(builder);
        assert (result.actionGet(200).equals("iid"));
    }

    public void testGetInteractions_Future() {
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onResponse(List.of());
            return null;
        }).when(interactionsIndex).getInteractions(anyString(), anyInt(), anyInt(), any());
        ActionFuture<List<Interaction>> result = cmHandler.getInteractions("cid", 0, 10);
        assert (result.actionGet(200).size() == 0);
    }

    public void testGetConversations_Future() {
        doAnswer(invocation -> {
            ActionListener<List<ConversationMeta>> al = invocation.getArgument(1);
            al.onResponse(List.of());
            return null;
        }).when(conversationMetaIndex).getConversations(anyInt(), any());
        ActionFuture<List<ConversationMeta>> result = cmHandler.getConversations(10);
        assert (result.actionGet(200).size() == 0);
    }

    public void testGetConversations_Page_Future() {
        doAnswer(invocation -> {
            ActionListener<List<ConversationMeta>> al = invocation.getArgument(2);
            al.onResponse(List.of());
            return null;
        }).when(conversationMetaIndex).getConversations(anyInt(), anyInt(), any());
        ActionFuture<List<ConversationMeta>> result = cmHandler.getConversations(30, 10);
        assert (result.actionGet(200).size() == 0);
    }

    public void testGetTraces() {
        doAnswer(invocation -> {
            ActionListener<List<Interaction>> al = invocation.getArgument(3);
            al.onResponse(List.of());
            return null;
        }).when(interactionsIndex).getTraces(any(), anyInt(), anyInt(), any());
        ActionListener<List<Interaction>> getTracesListener = mock(ActionListener.class);
        cmHandler.getTraces("iId", 0, 10, getTracesListener);
        ArgumentCaptor<List<Interaction>> argCaptor = ArgumentCaptor.forClass(List.class);
        verify(getTracesListener, times(1)).onResponse(argCaptor.capture());
        assert (argCaptor.getValue().size() == 0);
    }

    public void testUpdateConversation() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> al = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
            al.onResponse(updateResponse);
            return null;
        }).when(conversationMetaIndex).updateConversation(any(), any(), any());

        ActionListener<UpdateResponse> updateConversationListener = mock(ActionListener.class);
        cmHandler.updateConversation("cId", new HashMap<>(), updateConversationListener);
        ArgumentCaptor<UpdateResponse> argCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(updateConversationListener, times(1)).onResponse(argCaptor.capture());
    }

    public void testDelete_NoAccess() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(false);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteListener = mock(ActionListener.class);
        cmHandler.deleteConversation("cid", deleteListener);
        ArgumentCaptor<OpenSearchStatusException> argCaptor = ArgumentCaptor.forClass(OpenSearchStatusException.class);
        verify(deleteListener).onFailure(argCaptor.capture());
        assert (argCaptor.getValue().getMessage().equals("Resources not found. Failed to delete the memory for cid"));
    }

    public void testDelete_ConversationMetaDeleteFalse_ThenFalse() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(false);
            return null;
        }).when(conversationMetaIndex).deleteConversation(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(interactionsIndex).deleteConversation(anyString(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteListener = mock(ActionListener.class);
        cmHandler.deleteConversation("cid", deleteListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_InteractionsDeleteFalse_ThenFalse() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).deleteConversation(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(false);
            return null;
        }).when(interactionsIndex).deleteConversation(anyString(), any());
        @SuppressWarnings("unchecked")
        ActionListener<Boolean> deleteListener = mock(ActionListener.class);
        cmHandler.deleteConversation("cid", deleteListener);
        ArgumentCaptor<Boolean> argCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(deleteListener, times(1)).onResponse(argCaptor.capture());
        assert (!argCaptor.getValue());
    }

    public void testDelete_AsFuture() {
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).checkAccess(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(conversationMetaIndex).deleteConversation(anyString(), any());
        doAnswer(invocation -> {
            ActionListener<Boolean> al = invocation.getArgument(1);
            al.onResponse(true);
            return null;
        }).when(interactionsIndex).deleteConversation(anyString(), any());
        ActionFuture<Boolean> result = cmHandler.deleteConversation("cid");
        assert (result.actionGet(200));
    }

    public void testSearchConversations_Future() {
        SearchRequest request = mock(SearchRequest.class);
        SearchResponse response = mock(SearchResponse.class);
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(conversationMetaIndex).searchConversations(any(), any());
        ActionFuture<SearchResponse> result = cmHandler.searchConversations(request);
        assert (result.actionGet().equals(response));
    }

    public void testSearchInteractions_Future() {
        SearchRequest request = mock(SearchRequest.class);
        SearchResponse response = mock(SearchResponse.class);
        String cid = "cid";
        doAnswer(invocation -> {
            ActionListener<SearchResponse> listener = invocation.getArgument(2);
            listener.onResponse(response);
            return null;
        }).when(interactionsIndex).searchInteractions(any(), any(), any());
        ActionFuture<SearchResponse> result = cmHandler.searchInteractions(cid, request);
        assert (result.actionGet().equals(response));
    }

    public void testGetAConversation_Future() {
        ConversationMeta response = new ConversationMeta("cid", Instant.now(), Instant.now(), "boring name", null, null);
        doAnswer(invocation -> {
            ActionListener<ConversationMeta> listener = invocation.getArgument(1);
            listener.onResponse(response);
            return null;
        }).when(conversationMetaIndex).getConversation(any(), any());
        ActionFuture<ConversationMeta> result = cmHandler.getConversation("cid");
        assert (result.actionGet().equals(response));
    }

    public void testGetAnInteraction_Future() {
        Interaction interaction = new Interaction(
            "iid",
            Instant.now(),
            Instant.now(),
            "cid",
            "inp",
            "pt",
            "rsp",
            "ogn",
            Collections.singletonMap("meta", "some meta")
        );
        doAnswer(invocation -> {
            ActionListener<Interaction> listener = invocation.getArgument(1);
            listener.onResponse(interaction);
            return null;
        }).when(interactionsIndex).getInteraction(any(), any());
        ActionFuture<Interaction> result = cmHandler.getInteraction("iid");
        assert (result.actionGet().equals(interaction));
    }

    public void testUpdateInteraction() {
        doAnswer(invocation -> {
            ActionListener<UpdateResponse> al = invocation.getArgument(2);
            ShardId shardId = new ShardId(new Index("indexName", "uuid"), 1);
            UpdateResponse updateResponse = new UpdateResponse(shardId, "taskId", 1, 1, 1, DocWriteResponse.Result.UPDATED);
            al.onResponse(updateResponse);
            return null;
        }).when(interactionsIndex).updateInteraction(any(), any(), any());

        ActionListener<UpdateResponse> updateInteractionListener = mock(ActionListener.class);
        cmHandler.updateInteraction("iId", new HashMap<>(), updateInteractionListener);
        ArgumentCaptor<UpdateResponse> argCaptor = ArgumentCaptor.forClass(UpdateResponse.class);
        verify(updateInteractionListener, times(1)).onResponse(argCaptor.capture());
    }
}
