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
package org.opensearch.ml.client;

import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.client.Client;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.DeleteConversationAction;
import org.opensearch.ml.memory.action.conversation.DeleteConversationRequest;
import org.opensearch.ml.memory.action.conversation.DeleteConversationResponse;
import org.opensearch.ml.memory.action.conversation.GetConversationsAction;
import org.opensearch.ml.memory.action.conversation.GetConversationsRequest;
import org.opensearch.ml.memory.action.conversation.GetConversationsResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class MemoryNodeClient implements MemoryClient {
    
    Client client;
    
    public void createConversation(CreateConversationRequest request, ActionListener<CreateConversationResponse> listener) {
        client.execute(CreateConversationAction.INSTANCE, request, listener);
    }

    
    public ActionFuture<CreateConversationResponse> createConversation(CreateConversationRequest request) {
        PlainActionFuture<CreateConversationResponse> fut = PlainActionFuture.newFuture();
        createConversation(request, fut);
        return fut;
    }

    
    public void createInteraction(CreateInteractionRequest request, ActionListener<CreateInteractionResponse> listener) {
        client.execute(CreateInteractionAction.INSTANCE, request, listener);
    }

    public ActionFuture<CreateInteractionResponse> createInteraction(CreateInteractionRequest request) {
        PlainActionFuture<CreateInteractionResponse> fut = PlainActionFuture.newFuture();
        createInteraction(request, fut);
        return fut;
    }

    public void getConversations(GetConversationsRequest request, ActionListener<GetConversationsResponse> listener) {
        client.execute(GetConversationsAction.INSTANCE, request, listener);
    }

    public ActionFuture<GetConversationsResponse> getConversations(GetConversationsRequest request) {
        PlainActionFuture<GetConversationsResponse> fut = PlainActionFuture.newFuture();
        getConversations(request, fut);
        return fut;
    }

    public void getInteractions(GetInteractionsRequest request, ActionListener<GetInteractionsResponse> listener) {
        client.execute(GetInteractionsAction.INSTANCE, request, listener);
    }

    public ActionFuture<GetInteractionsResponse> getInteractions(GetInteractionsRequest request) {
        PlainActionFuture<GetInteractionsResponse> fut = PlainActionFuture.newFuture();
        getInteractions(request, fut);
        return fut;
    }

    public void deleteConversation(DeleteConversationRequest request, ActionListener<DeleteConversationResponse> listener) {
        client.execute(DeleteConversationAction.INSTANCE, request, listener);
    }

    public ActionFuture<DeleteConversationResponse> deleteConversation(DeleteConversationRequest request) {
        PlainActionFuture<DeleteConversationResponse> fut = PlainActionFuture.newFuture();
        deleteConversation(request, fut);
        return fut;
    }
}
