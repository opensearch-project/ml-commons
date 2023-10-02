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

    /**
     * Create a conversation using the transport-layer CreateConversation Action
     * @param request Request containing (optional) conversation name
     * @param listener Gets the CreateConversationResponse (contains new conversation id)
     */
    void createConversation(CreateConversationRequest request, ActionListener<CreateConversationResponse> listener);

    /**
     * Create a conversation using the transport-layer CreateConversation Action
     * @param request Request containing (optional) conversation name
     * @return ActionFuture for the CreateConversationResponse (contains new conversation id)
     */
    ActionFuture<CreateConversationResponse> createConversation(CreateConversationRequest request);

    /**
     * Create an interaction using the transport-layer CreateInteraction Action
     * @param request Request containing interaction fields
     * @param listener Gets the CreateInteractionResponse containing the id of the new interaction
     */
    void createInteraction(CreateInteractionRequest request, ActionListener<CreateInteractionResponse> listener);

    /**
     * Create an interaction using the transport-layer CreateInteraction Action
     * @param request Request containing interaction fields
     * @return ActionFuture for the CreateInteractionResponse containing the id of the new interaction
     */
    ActionFuture<CreateInteractionResponse> createInteraction(CreateInteractionRequest request);

    /**
     * Get a list of conversations using the transport-layer GetConversations Action
     * @param request Request containing number of interactions to get and what page to get them from
     * @param listener Gets the GetConversationsResponse containing the list of conversations
     */
    void getConversations(GetConversationsRequest request, ActionListener<GetConversationsResponse> listener);

    /**
     * Get a list of conversations using the transport-layer GetConversations Action
     * @param request Request containing number of interactions to get and what page to get them from
     * @return ActionFuture for the GetConversationsResponse containing the list of conversations
     */
    ActionFuture<GetConversationsResponse> getConversations(GetConversationsRequest request);

    /**
     * Get a list of interactions belonging to a conversation using the transport-layer GetInteractions Action
     * @param request Request containing the conversationId, number of interactions to get, and page to get them from
     * @param listener Gets the GetInteractionsResponse containing the list of interactions
     */
    void getInteractions(GetInteractionsRequest request, ActionListener<GetInteractionsResponse> listener);

    /**
     * Get a list of interactions belonging to a conversation using the transport-layer GetInteractions Action
     * @param request Request containing the conversationId, number of interactions to get, and page to get them from
     * @return ActionFuture for the GetInteractionsResponse containing the list of interactions
     */
    ActionFuture<GetInteractionsResponse> getInteractions(GetInteractionsRequest request);

    /**
     * Delete a conversation and all of its interactions
     * @param request Request containing the id of the conversation to delete
     * @param listener Gets the DeleteConversationsResponse containing whether the deletion was successful
     */
    void deleteConversation(DeleteConversationRequest request, ActionListener<DeleteConversationResponse> listener);

    /**
     * Delete a conversation and all of its interactions
     * @param request Request containing the id of the conversation to delete
     * @return ActionFuture for the DeleteConversationsResponse containing whether the deletion was successful
     */
    ActionFuture<DeleteConversationResponse> deleteConversation(DeleteConversationRequest request);
}
