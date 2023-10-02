package org.opensearch.ml.client;

import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.DeleteConversationRequest;
import org.opensearch.ml.memory.action.conversation.DeleteConversationResponse;
import org.opensearch.ml.memory.action.conversation.GetConversationsRequest;
import org.opensearch.ml.memory.action.conversation.GetConversationsResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;

public interface MemoryClient {

    void createConversation(CreateConversationRequest request, ActionListener<CreateConversationResponse> listener);

    ActionFuture<CreateConversationResponse> createConversation(CreateConversationRequest request);

    void createInteraction(CreateInteractionRequest request, ActionListener<CreateInteractionResponse> listener);

    ActionFuture<CreateInteractionResponse> createInteraction(CreateInteractionRequest request);

    void getConversations(GetConversationsRequest request, ActionListener<GetConversationsResponse> listener);

    ActionFuture<GetConversationsResponse> getConversations(GetConversationsRequest request);

    void getInteractions(GetInteractionsRequest request, ActionListener<GetInteractionsResponse> listener);

    ActionFuture<GetInteractionsResponse> getInteractions(GetInteractionsRequest request);

    void deleteConversation(DeleteConversationRequest request, ActionListener<DeleteConversationResponse> listener);

    ActionFuture<DeleteConversationResponse> deleteConversation(DeleteConversationRequest request);
}
