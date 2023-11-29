/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.memory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchSecurityException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.action.conversation.CreateConversationAction;
import org.opensearch.ml.memory.action.conversation.CreateConversationRequest;
import org.opensearch.ml.memory.action.conversation.CreateConversationResponse;
import org.opensearch.ml.memory.action.conversation.CreateInteractionAction;
import org.opensearch.ml.memory.action.conversation.CreateInteractionRequest;
import org.opensearch.ml.memory.action.conversation.CreateInteractionResponse;
import org.opensearch.ml.memory.index.ConversationMetaIndex;
import org.opensearch.ml.repackage.com.google.common.annotations.VisibleForTesting;
import org.opensearch.ml.repackage.com.google.common.base.Preconditions;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Memory manager for Memories. It contains ML memory related operations like create, read interactions etc.
 */
@Log4j2
@AllArgsConstructor
public class MLMemoryManager {

    private Client client;
    private ClusterService clusterService;
    private ConversationMetaIndex conversationMetaIndex;
    private final String indexName = ConversationalIndexConstants.INTERACTIONS_INDEX_NAME;

    /**
     * Create a new Conversation
     * @param name the name of the conversation
     * @param applicationType the application type that creates this conversation
     * @param actionListener action listener to process the response
     */
    public void createConversation(String name, String applicationType, ActionListener<CreateConversationResponse> actionListener) {
        try {
            client.execute(CreateConversationAction.INSTANCE, new CreateConversationRequest(name, applicationType), actionListener);
        } catch (RuntimeException exception) {
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
        } catch (RuntimeException exception) {
            actionListener.onFailure(exception);
        }
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param conversationId the conversation whose interactions to get
     * @param lastNInteraction Return how many interactions
     * @param actionListener get all the final interactions that are not traces
     */
    public void getFinalInteractions(String conversationId, int lastNInteraction, ActionListener<List<Interaction>> actionListener) {
        Preconditions.checkArgument(lastNInteraction > 0, "lastN must be at least 1.");
        log.info("Getting Interactions, conversationId {}, lastN {}", conversationId, lastNInteraction);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            if (!clusterService.state().metadata().hasIndex(indexName)) {
                actionListener.onResponse(List.of());
                return;
            }
            ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
                if (access) {
                    innerGetFinalInteractions(conversationId, lastNInteraction, actionListener);
                } else {
                    String userstr = client
                        .threadPool()
                        .getThreadContext()
                        .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                    String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
                    throw new OpenSearchSecurityException("User [" + user + "] does not have access to conversation " + conversationId);
                }
            }, e -> { actionListener.onFailure(e); });
            conversationMetaIndex.checkAccess(conversationId, accessListener);
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    void innerGetFinalInteractions(String conversationId, int lastNInteraction, ActionListener<List<Interaction>> listener) {
        SearchRequest searchRequest = Requests.searchRequest(indexName);

        // Build the query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // Add the ExistsQueryBuilder for checking null values
        ExistsQueryBuilder existsQueryBuilder = QueryBuilders.existsQuery(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD);
        boolQueryBuilder.mustNot(existsQueryBuilder);

        // Add the TermQueryBuilder for another field
        TermQueryBuilder termQueryBuilder = QueryBuilders
            .termQuery(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, conversationId);
        boolQueryBuilder.must(termQueryBuilder);

        // Set the query to the search source
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);

        searchRequest.source().size(lastNInteraction);
        searchRequest.source().sort(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, SortOrder.DESC);

        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<Interaction>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                List<Interaction> result = new LinkedList<Interaction>();
                for (SearchHit hit : response.getHits()) {
                    result.add(0, Interaction.fromSearchHit(hit));
                }
                internalListener.onResponse(result);
            }, e -> { internalListener.onFailure(e); });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(indexName), ActionListener.wrap(r -> { client.search(searchRequest, al); }, e -> {
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param parentInteractionId the parent interaction id whose traces to get
     * @param actionListener get all the trace interactions that are only traces
     */
    public void getTraces(String parentInteractionId, ActionListener<List<Interaction>> actionListener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            if (!clusterService.state().metadata().hasIndex(indexName)) {
                actionListener.onResponse(List.of());
                return;
            }
            innerGetTraces(parentInteractionId, actionListener);
        } catch (Exception e) {
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    void innerGetTraces(String parentInteractionId, ActionListener<List<Interaction>> listener) {
        SearchRequest searchRequest = Requests.searchRequest(indexName);

        // Build the query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // Add the ExistsQueryBuilder for checking null values
        ExistsQueryBuilder existsQueryBuilder = QueryBuilders.existsQuery(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD);
        boolQueryBuilder.must(existsQueryBuilder);

        // Add the TermQueryBuilder for another field
        TermQueryBuilder termQueryBuilder = QueryBuilders
            .termQuery(ConversationalIndexConstants.PARENT_INTERACTIONS_ID_FIELD, parentInteractionId);
        boolQueryBuilder.must(termQueryBuilder);

        // Set the query to the search source
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);
        searchRequest.source(searchSourceBuilder);

        searchRequest.source().sort(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD, SortOrder.ASC);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<Interaction>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                List<Interaction> result = new LinkedList<Interaction>();
                for (SearchHit hit : response.getHits()) {
                    result.add(Interaction.fromSearchHit(hit));
                }
                internalListener.onResponse(result);
            }, e -> { internalListener.onFailure(e); });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(indexName), ActionListener.wrap(r -> { client.search(searchRequest, al); }, e -> {
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Get the interactions associate with this conversation, sorted by recency
     * @param interactionId the parent interaction id whose traces to get
     * @param actionListener listener for the update response
     */
    public void updateInteraction(String interactionId, Map<String, Object> updateContent, ActionListener<UpdateResponse> actionListener) {
        UpdateRequest updateRequest = new UpdateRequest(indexName, interactionId);
        updateRequest.doc(updateContent);
        updateRequest.docAsUpsert(true);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> al = ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
                if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                    log.info("Failed to update the interaction with ID: {}", interactionId);
                    actionListener.onResponse(updateResponse);
                    return;
                }
                log.info("Successfully updated the interaction with ID: {}", interactionId);
                actionListener.onResponse(updateResponse);
            }, exception -> {
                log.error("Failed to update interaction with ID {}. Details: {}", interactionId, exception);
                actionListener.onFailure(exception);
            }), context::restore);
            client.update(updateRequest, al);
        } catch (Exception e) {
            log.error("Failed to update interaction for interaction id {}. Details {}:", interactionId, e);
            actionListener.onFailure(e);
        }
    }
}
