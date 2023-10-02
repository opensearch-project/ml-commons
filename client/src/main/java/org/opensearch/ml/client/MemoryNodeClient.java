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
import lombok.RequiredArgsConstructor;

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
