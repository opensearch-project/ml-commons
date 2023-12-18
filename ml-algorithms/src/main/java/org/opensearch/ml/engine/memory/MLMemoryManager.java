/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.action.conversation.GetInteractionsAction;
import org.opensearch.ml.memory.action.conversation.GetInteractionsRequest;
import org.opensearch.ml.memory.action.conversation.GetInteractionsResponse;
import org.opensearch.ml.memory.action.conversation.GetTracesAction;
import org.opensearch.ml.memory.action.conversation.GetTracesRequest;
import org.opensearch.ml.memory.action.conversation.GetTracesResponse;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionAction;
import org.opensearch.ml.memory.action.conversation.UpdateInteractionRequest;

import com.google.common.base.Preconditions;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Memory manager for Memories. It contains ML memory related operations like create, read interactions etc.
 */
@Log4j2
@AllArgsConstructor
public class MLMemoryManager {

    private Client client;

    /**
     * Create a new Conversation
     * @param name the name of the conversation
     * @param applicationType the application type that creates this conversation
     * @param actionListener action listener to process the response
     */
    public void createConversation(String name, String applicationType, ActionListener<CreateConversationResponse> actionListener) {
        try {
            client.execute(CreateConversationAction.INSTANCE, new CreateConversationRequest(name, applicationType), actionListener);
        } catch (Exception exception) {
            actionListener.onFailure(exception);
        }
    }

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the Gen AI response for this interaction
     * @param origin the name of the GenAI agent in this interaction
     * @param additionalInfo additional information used in constructing the LLM prompt
     * @param parentIntId the parent interactionId of this interaction
     * @param traceNum the trace number for a parent interaction
     * @param actionListener gets the ID of the new interaction
     */
    public void createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo,
        String parentIntId,
        Integer traceNum,
        ActionListener<CreateInteractionResponse> actionListener
    ) {
        Preconditions.checkNotNull(conversationId);
        Preconditions.checkNotNull(input);
        Preconditions.checkNotNull(response);
        // additionalInfo cannot be null as flat object
        additionalInfo = (additionalInfo == null) ? new HashMap<>() : additionalInfo;
        try {
            client
                .execute(
                    CreateInteractionAction.INSTANCE,
                    new CreateInteractionRequest(
                        conversationId,
                        input,
                        promptTemplate,
                        response,
                        origin,
                        additionalInfo,
                        parentIntId,
                        traceNum
                    ),
                    actionListener
                );
        } catch (Exception exception) {
            actionListener.onFailure(exception);
        }
    }

    /**
     * Get the interactions associate with this conversation that are not traces, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param lastNInteraction Return how many interactions
     * @param actionListener get all the final interactions that are not traces
     */
    public void getFinalInteractions(String conversationId, int lastNInteraction, ActionListener<List<Interaction>> actionListener) {
        Preconditions.checkNotNull(conversationId);
        Preconditions.checkArgument(lastNInteraction > 0, "lastN must be at least 1.");
        log.debug("Getting Interactions, conversationId {}, lastN {}", conversationId, lastNInteraction);

        ActionListener<GetInteractionsResponse> al = ActionListener.wrap(getInteractionsResponse -> {
            actionListener.onResponse(getInteractionsResponse.getInteractions());
        }, e -> { actionListener.onFailure(e); });

        try {
            client.execute(GetInteractionsAction.INSTANCE, new GetInteractionsRequest(conversationId, lastNInteraction), al);
        } catch (Exception exception) {
            actionListener.onFailure(exception);
        }
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param parentInteractionId the parent interaction id whose traces to get
     * @param actionListener get all the trace interactions that are only traces
     */
    public void getTraces(String parentInteractionId, ActionListener<List<Interaction>> actionListener) {
        Preconditions.checkNotNull(parentInteractionId);
        log.debug("Getting traces for conversationId {}", parentInteractionId);

        ActionListener<GetTracesResponse> al = ActionListener.wrap(getTracesResponse -> {
            actionListener.onResponse(getTracesResponse.getTraces());
        }, e -> { actionListener.onFailure(e); });

        try {
            client.execute(GetTracesAction.INSTANCE, new GetTracesRequest(parentInteractionId), al);
        } catch (Exception exception) {
            actionListener.onFailure(exception);
        }
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param interactionId the parent interaction id whose traces to get
     * @param actionListener listener for the update response
     */
    public void updateInteraction(String interactionId, Map<String, Object> updateContent, ActionListener<UpdateResponse> actionListener) {
        Preconditions.checkNotNull(interactionId);
        Preconditions.checkNotNull(updateContent);
        try {
            client.execute(UpdateInteractionAction.INSTANCE, new UpdateInteractionRequest(interactionId, updateContent), actionListener);
        } catch (Exception exception) {
            actionListener.onFailure(exception);
        }
    }
}
