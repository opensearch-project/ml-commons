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
package org.opensearch.searchpipelines.questionanswering.generative.client;

import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.mockito.ArgumentCaptor;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.transport.client.Client;

public class ConversationalMemoryClientTests extends OpenSearchTestCase {

    public void testCreateConversation() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        ArgumentCaptor<CreateConversationRequest> captor = ArgumentCaptor.forClass(CreateConversationRequest.class);
        String conversationId = UUID.randomUUID().toString();
        CreateConversationResponse response = new CreateConversationResponse(conversationId);
        ActionFuture<CreateConversationResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(response);
        when(client.execute(eq(CreateConversationAction.INSTANCE), any())).thenReturn(future);
        String name = "foo";
        String actual = memoryClient.createConversation(name);
        verify(client, times(1)).execute(eq(CreateConversationAction.INSTANCE), captor.capture());
        assertEquals(name, captor.getValue().getName());
        assertEquals(conversationId, actual);
    }

    public void testGetInteractionsNoPagination() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        int lastN = 5;
        String conversationId = UUID.randomUUID().toString();
        List<Interaction> interactions = new ArrayList<>();
        IntStream
            .range(0, lastN)
            .forEach(
                i -> interactions
                    .add(new Interaction(Integer.toString(i), Instant.now(), Instant.now(), conversationId, "foo", "bar", "x", "y", null))
            );
        GetInteractionsResponse response = new GetInteractionsResponse(interactions, lastN, false);
        ActionFuture<GetInteractionsResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(response);
        when(client.execute(eq(GetInteractionsAction.INSTANCE), any())).thenReturn(future);
        ArgumentCaptor<GetInteractionsRequest> captor = ArgumentCaptor.forClass(GetInteractionsRequest.class);

        List<Interaction> actual = memoryClient.getInteractions(conversationId, lastN);
        verify(client, times(1)).execute(eq(GetInteractionsAction.INSTANCE), captor.capture());
        GetInteractionsRequest actualRequest = captor.getValue();
        assertEquals(lastN, actual.size());
        assertEquals(conversationId, actualRequest.getConversationId());
        assertEquals(lastN, actualRequest.getMaxResults());
        assertEquals(0, actualRequest.getFrom());
    }

    public void testGetInteractionsWithPagination() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        int lastN = 5;
        String conversationId = UUID.randomUUID().toString();
        List<Interaction> firstPage = new ArrayList<>();
        IntStream
            .range(0, lastN)
            .forEach(
                i -> firstPage
                    .add(new Interaction(Integer.toString(i), Instant.now(), Instant.now(), conversationId, "foo", "bar", "x", "y", null))
            );
        GetInteractionsResponse response1 = new GetInteractionsResponse(firstPage, lastN, true);
        List<Interaction> secondPage = new ArrayList<>();
        IntStream
            .range(0, lastN)
            .forEach(
                i -> secondPage
                    .add(new Interaction(Integer.toString(i), Instant.now(), Instant.now(), conversationId, "foo", "bar", "x", "y", null))
            );
        GetInteractionsResponse response2 = new GetInteractionsResponse(secondPage, lastN, false);
        ActionFuture<GetInteractionsResponse> future1 = mock(ActionFuture.class);
        when(future1.actionGet(anyLong())).thenReturn(response1);
        ActionFuture<GetInteractionsResponse> future2 = mock(ActionFuture.class);
        when(future2.actionGet(anyLong())).thenReturn(response2);
        when(client.execute(eq(GetInteractionsAction.INSTANCE), any())).thenReturn(future1).thenReturn(future2);
        ArgumentCaptor<GetInteractionsRequest> captor = ArgumentCaptor.forClass(GetInteractionsRequest.class);

        List<Interaction> actual = memoryClient.getInteractions(conversationId, 2 * lastN);
        // Called twice
        verify(client, times(2)).execute(eq(GetInteractionsAction.INSTANCE), captor.capture());
        List<GetInteractionsRequest> actualRequests = captor.getAllValues();
        assertEquals(2 * lastN, actual.size());
        assertEquals(conversationId, actualRequests.get(0).getConversationId());
        assertEquals(2 * lastN, actualRequests.get(0).getMaxResults());
        assertEquals(0, actualRequests.get(0).getFrom());
        assertEquals(lastN, actualRequests.get(1).getFrom());
    }

    public void testGetInteractionsNoMoreResults() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        int lastN = 5;
        int found = lastN - 1;
        String conversationId = UUID.randomUUID().toString();
        List<Interaction> interactions = new ArrayList<>();
        // Return fewer results than requested
        IntStream
            .range(0, found)
            .forEach(
                i -> interactions
                    .add(new Interaction(Integer.toString(i), Instant.now(), Instant.now(), conversationId, "foo", "bar", "x", "y", null))
            );
        GetInteractionsResponse response = new GetInteractionsResponse(interactions, found, false);
        ActionFuture<GetInteractionsResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(response);
        when(client.execute(eq(GetInteractionsAction.INSTANCE), any())).thenReturn(future);
        ArgumentCaptor<GetInteractionsRequest> captor = ArgumentCaptor.forClass(GetInteractionsRequest.class);

        List<Interaction> actual = memoryClient.getInteractions(conversationId, lastN);
        verify(client, times(1)).execute(eq(GetInteractionsAction.INSTANCE), captor.capture());
        GetInteractionsRequest actualRequest = captor.getValue();
        assertEquals(found, actual.size());
        assertEquals(conversationId, actualRequest.getConversationId());
        assertEquals(lastN, actualRequest.getMaxResults());
        assertEquals(0, actualRequest.getFrom());
    }

    public void testAvoidInfiniteLoop() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        GetInteractionsResponse response1 = new GetInteractionsResponse(null, 0, true);
        GetInteractionsResponse response2 = new GetInteractionsResponse(List.of(), 0, true);
        ActionFuture<GetInteractionsResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(response1).thenReturn(response2);
        when(client.execute(eq(GetInteractionsAction.INSTANCE), any())).thenReturn(future);
        List<Interaction> actual = memoryClient.getInteractions("1", 10);
        assertTrue(actual.isEmpty());
        actual = memoryClient.getInteractions("1", 10);
        assertTrue(actual.isEmpty());
    }

    public void testNoResults() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        GetInteractionsResponse response1 = new GetInteractionsResponse(null, 0, true);
        GetInteractionsResponse response2 = new GetInteractionsResponse(List.of(), 0, false);
        ActionFuture<GetInteractionsResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(response1).thenReturn(response2);
        when(client.execute(eq(GetInteractionsAction.INSTANCE), any())).thenReturn(future);
        List<Interaction> actual = memoryClient.getInteractions("1", 10);
        assertTrue(actual.isEmpty());
        actual = memoryClient.getInteractions("1", 10);
        assertTrue(actual.isEmpty());
    }

    public void testCreateInteraction() {
        Client client = mock(Client.class);
        ConversationalMemoryClient memoryClient = new ConversationalMemoryClient(client);
        String id = UUID.randomUUID().toString();
        CreateInteractionResponse res = new CreateInteractionResponse(id);
        ActionFuture<CreateInteractionResponse> future = mock(ActionFuture.class);
        when(future.actionGet(anyLong())).thenReturn(res);
        when(client.execute(eq(CreateInteractionAction.INSTANCE), any())).thenReturn(future);
        String actual = memoryClient
            .createInteraction("cid", "input", "prompt", "answer", "origin", Collections.singletonMap("metadata", "hits"));
        assertEquals(id, actual);
    }
}
