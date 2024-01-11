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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.opensearch.action.StepListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.PlainActionFuture;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.action.ActionFuture;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.common.conversation.Interaction.InteractionBuilder;
import org.opensearch.ml.memory.ConversationalMemoryHandler;

import lombok.extern.log4j.Log4j2;

/**
 * Class for handling all Conversational Memory operactions
 */
@Log4j2
public class OpenSearchConversationalMemoryHandler implements ConversationalMemoryHandler {

    private ConversationMetaIndex conversationMetaIndex;
    private InteractionsIndex interactionsIndex;

    /**
     * Constructor
     * @param client opensearch client to use for talking to OS
     * @param clusterService ClusterService object for managing OS
     */
    public OpenSearchConversationalMemoryHandler(Client client, ClusterService clusterService) {
        this.conversationMetaIndex = new ConversationMetaIndex(client, clusterService);
        this.interactionsIndex = new InteractionsIndex(client, clusterService, this.conversationMetaIndex);
    }

    // VisibleForTesting
    OpenSearchConversationalMemoryHandler(ConversationMetaIndex conversationMetaIndex, InteractionsIndex interactionsIndex) {
        this.conversationMetaIndex = conversationMetaIndex;
        this.interactionsIndex = interactionsIndex;
    }

    /**
     * Create a new conversation
     * @param listener listener to wait for this op to finish, gets unique id of new conversation
     */
    public void createConversation(ActionListener<String> listener) {
        conversationMetaIndex.createConversation(listener);
    }

    /**
     * Create a new conversation
     * @return ActionFuture for the conversationId of the new conversation
     */
    public ActionFuture<String> createConversation() {
        PlainActionFuture<String> fut = PlainActionFuture.newFuture();
        createConversation(fut);
        return fut;
    }

    /**
     * Create a new conversation
     * @param name the name of the new conversation
     * @param listener listener to wait for this op to finish, gets unique id of new conversation
     */
    public void createConversation(String name, ActionListener<String> listener) {
        conversationMetaIndex.createConversation(name, listener);
    }

    /**
     * Create a new conversation
     * @param name the name of the new conversation
     * @param applicationType the application that creates this conversation
     * @param listener listener to wait for this op to finish, gets unique id of new conversation
     */
    public void createConversation(String name, String applicationType, ActionListener<String> listener) {
        conversationMetaIndex.createConversation(name, applicationType, listener);
    }

    /**
     * Create a new conversation
     * @param name the name of the new conversation
     * @return ActionFuture for the conversationId of the new conversation
     */
    public ActionFuture<String> createConversation(String name) {
        PlainActionFuture<String> fut = PlainActionFuture.newFuture();
        createConversation(name, fut);
        return fut;
    }

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the Gen AI response for this interaction
     * @param origin the name of the GenAI agent in this interaction
     * @param additionalInfo additional information used in constructing the LLM prompt
     * @param listener gets the ID of the new interaction
     */
    public void createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo,
        ActionListener<String> listener
    ) {
        Instant time = Instant.now();
        interactionsIndex.createInteraction(conversationId, input, promptTemplate, response, origin, additionalInfo, time, listener);
    }

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the Gen AI response for this interaction
     * @param origin the name of the GenAI agent in this interaction
     * @param additionalInfo additional information used in constructing the LLM prompt
     * @param interactionId the parent interactionId of this interaction
     * @param traceNumber the trace number for a parent interaction
     * @param listener gets the ID of the new interaction
     */
    public void createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo,
        ActionListener<String> listener,
        String interactionId,
        Integer traceNumber
    ) {
        Instant time = Instant.now();
        interactionsIndex
            .createInteraction(
                conversationId,
                input,
                promptTemplate,
                response,
                origin,
                additionalInfo,
                time,
                listener,
                interactionId,
                traceNumber
            );
    }

    /**
     * Adds an interaction to the conversation indicated, updating the conversational metadata
     * @param conversationId the conversation to add the interaction to
     * @param input the human input for the interaction
     * @param promptTemplate the prompt template used in this interaction
     * @param response the Gen AI response for this interaction
     * @param origin the name of the GenAI agent in this interaction
     * @param additionalInfo additional information used in constructing the LLM prompt
     * @return ActionFuture for the interactionId of the new interaction
     */
    public ActionFuture<String> createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo
    ) {
        PlainActionFuture<String> fut = PlainActionFuture.newFuture();
        createInteraction(conversationId, input, promptTemplate, response, origin, additionalInfo, fut);
        return fut;
    }

    /**
     * Adds an interaction to the index, updating the associated Conversational Metadata
     * @param builder Interaction builder that creates the Interaction to be added. id should be null
     * @param listener gets the interactionId of the newly created interaction
     */
    public void createInteraction(InteractionBuilder builder, ActionListener<String> listener) {
        builder.createTime(Instant.now());
        Interaction interaction = builder.build();
        interactionsIndex
            .createInteraction(
                interaction.getConversationId(),
                interaction.getInput(),
                interaction.getPromptTemplate(),
                interaction.getResponse(),
                interaction.getOrigin(),
                interaction.getAdditionalInfo(),
                interaction.getCreateTime(),
                listener
            );
    }

    /**
     * Adds an interaction to the index, updating the associated Conversational Metadata
     * @param builder Interaction builder that creates the Interaction to be added. id should be null
     * @return ActionFuture for the interactionId of the newly created interaction
     */
    public ActionFuture<String> createInteraction(InteractionBuilder builder) {
        PlainActionFuture<String> fut = PlainActionFuture.newFuture();
        createInteraction(builder, fut);
        return fut;
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param from where to start listing from
     * @param maxResults how many interactions to get
     * @param listener gets the list of interactions in this conversation, sorted by recency
     */
    public void getInteractions(String conversationId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        interactionsIndex.getInteractions(conversationId, from, maxResults, listener);
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param from where to start listing from
     * @param maxResults how many interactions to get
     * @return ActionFuture the list of interactions in this conversation, sorted by recency
     */
    public ActionFuture<List<Interaction>> getInteractions(String conversationId, int from, int maxResults) {
        PlainActionFuture<List<Interaction>> fut = PlainActionFuture.newFuture();
        getInteractions(conversationId, from, maxResults, fut);
        return fut;
    }

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param from where to start listing from
     * @param maxResults how many conversations to list
     * @param listener gets the list of all conversations, sorted by recency
     */
    public void getConversations(int from, int maxResults, ActionListener<List<ConversationMeta>> listener) {
        conversationMetaIndex.getConversations(from, maxResults, listener);
    }

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param from where to start listing from
     * @param maxResults how many conversations to list
     * @return ActionFuture for the list of all conversations, sorted by recency
     */
    public ActionFuture<List<ConversationMeta>> getConversations(int from, int maxResults) {
        PlainActionFuture<List<ConversationMeta>> fut = PlainActionFuture.newFuture();
        getConversations(from, maxResults, fut);
        return fut;
    }

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param maxResults how many conversations to get
     * @param listener receives the list of conversations, sorted by recency
     */
    public void getConversations(int maxResults, ActionListener<List<ConversationMeta>> listener) {
        conversationMetaIndex.getConversations(maxResults, listener);
    }

    /**
     * Get all conversations (not the interactions in them, just the headers)
     * @param maxResults how many conversations to list
     * @return ActionFuture for the list of all conversations, sorted by recency
     */
    public ActionFuture<List<ConversationMeta>> getConversations(int maxResults) {
        PlainActionFuture<List<ConversationMeta>> fut = PlainActionFuture.newFuture();
        getConversations(maxResults, fut);
        return fut;
    }

    /**
     * Delete a conversation and all of its interactions
     * @param conversationId the id of the conversation to delete
     * @param listener receives whether the conversationMeta object and all of its interactions were deleted. i.e. false => there's something still in an index somewhere
     */
    public void deleteConversation(String conversationId, ActionListener<Boolean> listener) {
        StepListener<Boolean> accessListener = new StepListener<>();
        conversationMetaIndex.checkAccess(conversationId, accessListener);
        log.info("DELETING CONVERSATION " + conversationId);
        accessListener.whenComplete(access -> {
            if (access) {
                StepListener<Boolean> metaDeleteListener = new StepListener<>();
                StepListener<Boolean> interactionsListener = new StepListener<>();

                interactionsIndex.deleteConversation(conversationId, interactionsListener);

                interactionsListener
                    .whenComplete(
                        interactionResult -> { conversationMetaIndex.deleteConversation(conversationId, metaDeleteListener); },
                        listener::onFailure
                    );

                metaDeleteListener.whenComplete(metaDeleteResult -> {
                    log.info("SUCCESSFUL DELETION OF CONVERSATION " + conversationId);
                    listener.onResponse(metaDeleteResult && interactionsListener.result());
                }, listener::onFailure);

            } else {
                listener.onResponse(false);
            }
        }, listener::onFailure);
    }

    /**
     * Delete a conversation and all of its interactions
     * @param conversationId the id of the conversation to delete
     * @return ActionFuture for whether the conversationMeta object and all of its interactions were deleted. i.e. false => there's something still in an index somewhere
     */
    public ActionFuture<Boolean> deleteConversation(String conversationId) {
        PlainActionFuture<Boolean> fut = PlainActionFuture.newFuture();
        deleteConversation(conversationId, fut);
        return fut;
    }

    /**
     * Search over conversations index
     * @param request search request over the conversations index
     * @param listener receives the search response
     */
    public void searchConversations(SearchRequest request, ActionListener<SearchResponse> listener) {
        conversationMetaIndex.searchConversations(request, listener);
    }

    /**
     * Search over conversations index
     * @param request search request over the conversations index
     * @return ActionFuture for the search response
     */
    public ActionFuture<SearchResponse> searchConversations(SearchRequest request) {
        PlainActionFuture<SearchResponse> fut = PlainActionFuture.newFuture();
        searchConversations(request, fut);
        return fut;
    }

    /**
     * Search over interactions of a conversation
     * @param conversationId id of the conversation to search through
     * @param request search request over the interactions
     * @param listener receives the search response
     */
    public void searchInteractions(String conversationId, SearchRequest request, ActionListener<SearchResponse> listener) {
        interactionsIndex.searchInteractions(conversationId, request, listener);
    }

    /**
     * Search over interactions of a conversation
     * @param conversationId id of the conversation to search through
     * @param request search request over the interactions
     * @return ActionFuture for the search response
     */
    public ActionFuture<SearchResponse> searchInteractions(String conversationId, SearchRequest request) {
        PlainActionFuture<SearchResponse> fut = PlainActionFuture.newFuture();
        searchInteractions(conversationId, request, fut);
        return fut;
    }

    public void getTraces(String interactionId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        interactionsIndex.getTraces(interactionId, from, maxResults, listener);
    }

    public void updateConversation(String conversationId, Map<String, Object> updateContent, ActionListener<UpdateResponse> listener) {
        UpdateRequest updateRequest = new UpdateRequest(ConversationalIndexConstants.META_INDEX_NAME, conversationId);
        updateContent.putIfAbsent(ConversationalIndexConstants.META_UPDATED_TIME_FIELD, Instant.now());

        updateRequest.doc(updateContent);
        updateRequest.docAsUpsert(true);
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        conversationMetaIndex.updateConversation(updateRequest, listener);
    }

    /**
     * Get a single ConversationMeta object
     * @param conversationId id of the conversation to get
     * @param listener receives the conversationMeta object
     */
    public void getConversation(String conversationId, ActionListener<ConversationMeta> listener) {
        conversationMetaIndex.getConversation(conversationId, listener);
    }

    /**
     * Get a single ConversationMeta object
     * @param conversationId id of the conversation to get
     * @return ActionFuture for the conversationMeta object
     */
    public ActionFuture<ConversationMeta> getConversation(String conversationId) {
        PlainActionFuture<ConversationMeta> fut = PlainActionFuture.newFuture();
        getConversation(conversationId, fut);
        return fut;
    }

    /**
     * Get a single interaction
     * @param conversationId id of the conversation this interaction belongs to
     * @param interactionId id of this interaction
     * @param listener receives the interaction
     */
    public void getInteraction(String conversationId, String interactionId, ActionListener<Interaction> listener) {
        interactionsIndex.getInteraction(conversationId, interactionId, listener);
    }

    /**
     * Get a single interaction
     * @param conversationId id of the conversation this interaction belongs to
     * @param interactionId id of this interaction
     * @return ActionFuture for the interaction
     */
    public ActionFuture<Interaction> getInteraction(String conversationId, String interactionId) {
        PlainActionFuture<Interaction> fut = PlainActionFuture.newFuture();
        getInteraction(conversationId, interactionId, fut);
        return fut;
    }

}
