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
package org.opensearch.ml.conversational;

import java.util.List;

import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversational.ConversationMeta;
import org.opensearch.ml.common.conversational.Interaction;
import org.opensearch.ml.common.conversational.Interaction.InteractionBuilder;

/**
 * Class for handling all Conversational Memory operactions
 */
public interface ConversationalMemoryHandler {

    /**
     * Create a new conversation
     * @param listener listener to wait for this op to finish, gets unique id of new conversation
     */
    public void createConversation(ActionListener<String> listener);

    /**
     * Create a new conversation
     * @return ActionFuture for the conversationId of the new conversation
     */
    public ActionFuture<String> createConversation();

    /**
     * Create a new conversation
     * @param name the name of the new conversation
     * @param listener listener to wait for this op to finish, gets unique id of new conversation
     */
    public void createConversation(String name, ActionListener<String> listener);

    /**
     * Create a new conversation
     * @param name the name of the new conversation
     * @return ActionFuture for the conversationId of the new conversation
     */
    public ActionFuture<String> createConversation(String name);

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param prompt the prompt template used in this interaction
     * @param response the Gen AI response for this interaction
     * @param agent the name of the GenAI agent in this interaction
     * @param metadata arbitrary JSON string of extra stuff
     * @param listener gets the ID of the new interaction
     */
    public void createInteraction(
        String conversationId,
        String input,
        String prompt,
        String response,
        String agent,
        String metadata,
        ActionListener<String> listener
    );

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param prompt the prompt template used in this interaction
     * @param response the Gen AI response for this interaction
     * @param agent the name of the GenAI agent in this interaction
     * @param metadata arbitrary JSON string of extra stuff
     * @return ActionFuture for the interactionId of the new interaction
     */
    public ActionFuture<String> createInteraction(
        String conversationId,
        String input,
        String prompt,
        String response,
        String agent,
        String metadata
    );

    /**
     * Adds an interaction to the index, updating the associated Conversational Metadata
     * @param builder Interaction builder that creates the Interaction to be added. id should be null
     * @param listener gets the interactionId of the newly created interaction
     */
    public void createInteraction(InteractionBuilder builder, ActionListener<String> listener);

    /**
     * Adds an interaction to the index, updating the associated Conversational Metadata
     * @param builder Interaction builder that creates the Interaction to be added. id should be null
     * @return ActionFuture for the interactionId of the newly created interaction
     */
    public ActionFuture<String> createInteraction(InteractionBuilder builder);

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param from where to start listiing from
     * @param maxResults how many interactions to get
     * @param listener gets the list of interactions in this conversation, sorted by recency
     */
    public void getInteractions(String conversationId, int from, int maxResults, ActionListener<List<Interaction>> listener);

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param from where to start listiing from
     * @param maxResults how many interactions to get
     * @return ActionFuture the list of interactions in this conversation, sorted by recency
     */
    public ActionFuture<List<Interaction>> getInteractions(String conversationId, int from, int maxResults);

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param from where to start listing from
     * @param maxResults how many conversations to list
     * @param listener gets the list of all conversations, sorted by recency
     */
    public void getConversations(int from, int maxResults, ActionListener<List<ConversationMeta>> listener);

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param from where to start listing from
     * @param maxResults how many conversations to list
     * @return ActionFuture for the list of all conversations, sorted by recency
     */
    public ActionFuture<List<ConversationMeta>> getConversations(int from, int maxResults);

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param maxResults how many conversations to get
     * @param listener receives the list of conversations, sorted by recency
     */
    public void getConversations(int maxResults, ActionListener<List<ConversationMeta>> listener);

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param maxResults how many conversations to list
     * @return ActionFuture for the list of all conversations, sorted by recency
     */
    public ActionFuture<List<ConversationMeta>> getConversations(int maxResults);

    /**
     * Delete a conversation and all of its interactions
     * @param conversationId the id of the conversation to delete
     * @param listener receives whether the conversationMeta object and all of its interactions were deleted. i.e. false => there's something still in an index somewhere
     */
    public void deleteConversation(String conversationId, ActionListener<Boolean> listener);

    /**
     * Delete a conversation and all of its interactions
     * @param conversationId the id of the conversation to delete
     * @return ActionFuture for whether the conversationMeta object and all of its interactions were deleted. i.e. false => there's something still in an index somewhere
     */
    public ActionFuture<Boolean> deleteConversation(String conversationId);

}
