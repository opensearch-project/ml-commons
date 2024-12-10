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

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INDEX_NAME;
import static org.opensearch.ml.common.utils.IndexUtils.DEFAULT_INDEX_SETTINGS;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.client.Requests;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.commons.ConfigConstants;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.ExistsQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.SortOrder;

import com.google.common.annotations.VisibleForTesting;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Class for handling the interactions index
 */
@Log4j2
@AllArgsConstructor
public class InteractionsIndex {

    private Client client;
    private ClusterService clusterService;
    private ConversationMetaIndex conversationMetaIndex;
    // How big the steps should be when gathering *ALL* interactions in a conversation
    private final int resultsAtATime = 300;

    /**
     * 'PUT's the index in opensearch if it's not there already
     * @param listener gets whether the index needed to be initialized. Throws error if it fails to init
     */
    public void initInteractionsIndexIfAbsent(ActionListener<Boolean> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            log.debug("No messages index found. Adding it");
            CreateIndexRequest request = Requests
                .createIndexRequest(INTERACTIONS_INDEX_NAME)
                .mapping(ConversationalIndexConstants.INTERACTIONS_MAPPINGS, XContentType.JSON)
                .settings(DEFAULT_INDEX_SETTINGS);
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                ActionListener<CreateIndexResponse> al = ActionListener.wrap(r -> {
                    if (r.equals(new CreateIndexResponse(true, true, INTERACTIONS_INDEX_NAME))) {
                        log.info("created index [" + INTERACTIONS_INDEX_NAME + "]");
                        internalListener.onResponse(true);
                    } else {
                        internalListener.onResponse(false);
                    }
                }, e -> {
                    if (e instanceof ResourceAlreadyExistsException
                        || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                        internalListener.onResponse(true);
                    } else {
                        log.error("Failed to create index [" + INTERACTIONS_INDEX_NAME + "]", e);
                        internalListener.onFailure(e);
                    }
                });
                client.admin().indices().create(request, al);
            } catch (Exception e) {
                if (e instanceof ResourceAlreadyExistsException
                    || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                    listener.onResponse(true);
                } else {
                    log.error("Failed to create index [" + INTERACTIONS_INDEX_NAME + "]", e);
                    listener.onFailure(e);
                }
            }
        } else {
            listener.onResponse(true);
        }
    }

    /**
     * Add an interaction to this index. Return the ID of the newly created interaction
     * @param conversationId The id of the conversation this interaction belongs to
     * @param input the user (human) input into this interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the GenAI response for this interaction
     * @param origin the origin of the response for this interaction
     * @param additionalInfo additional information used for constructing the LLM prompt
     * @param timestamp when this interaction happened
     * @param parintid the parent interactionId of this interaction
     * @param traceNumber the trace number for a parent interaction
     * @param listener gets the id of the newly created interaction record
     */
    public void createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo,
        Instant timestamp,
        ActionListener<String> listener,
        String parintid,
        Integer traceNumber
    ) {
        initInteractionsIndexIfAbsent(ActionListener.wrap(indexExists -> {
            String userstr = client
                .threadPool()
                .getThreadContext()
                .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
            String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
            if (indexExists) {
                this.conversationMetaIndex.checkAccess(conversationId, ActionListener.wrap(access -> {
                    if (access) {
                        Map<String, Object> sourceMap = new HashMap<>();
                        sourceMap.put(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, conversationId);
                        sourceMap.put(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, timestamp);
                        sourceMap.put(ConversationalIndexConstants.PARENT_INTERACTIONS_ID_FIELD, parintid);
                        sourceMap.put(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD, traceNumber);

                        if (input != null && !input.trim().isEmpty()) {
                            sourceMap.put(ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD, input);
                        }
                        if (promptTemplate != null && !promptTemplate.trim().isEmpty()) {
                            sourceMap.put(ConversationalIndexConstants.INTERACTIONS_PROMPT_TEMPLATE_FIELD, promptTemplate);
                        }
                        if (response != null && !response.trim().isEmpty()) {
                            sourceMap.put(ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD, response);
                        }
                        if (origin != null && !origin.trim().isEmpty()) {
                            sourceMap.put(ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD, origin);
                        }
                        if (additionalInfo != null && !additionalInfo.isEmpty()) {
                            sourceMap.put(ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD, additionalInfo);
                        }
                        IndexRequest request = Requests.indexRequest(INTERACTIONS_INDEX_NAME).source(sourceMap);
                        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                            ActionListener<String> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                            ActionListener<IndexResponse> al = ActionListener.wrap(resp -> {
                                if (resp.status() == RestStatus.CREATED) {
                                    internalListener.onResponse(resp.getId());
                                    log.info("Successfully created the message with id : {}", resp.getId());
                                } else {
                                    internalListener.onFailure(new IOException("Failed to create message"));
                                }
                            }, e -> { internalListener.onFailure(e); });
                            client.index(request, al);
                        } catch (Exception e) {
                            listener.onFailure(e);
                        }
                    } else {
                        throw new OpenSearchStatusException(
                            "User [" + user + "] does not have access to memory " + conversationId,
                            RestStatus.UNAUTHORIZED
                        );
                    }
                }, e -> { listener.onFailure(e); }));
            } else {
                listener.onFailure(new IOException("no index to add memory to"));
            }
        }, e -> { listener.onFailure(e); }));
    }

    /**
     * Add an interaction to this index. Return the ID of the newly created interaction
     * @param conversationId The id of the conversation this interaction belongs to
     * @param input the user (human) input into this interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the GenAI response for this interaction
     * @param origin the origin of the response for this interaction
     * @param additionalInfo additional information used for constructing the LLM prompt
     * @param timestamp when this interaction happened
     * @param listener gets the id of the newly created interaction record
     */
    public void createInteraction(
        String conversationId,
        String input,
        String promptTemplate,
        String response,
        String origin,
        Map<String, String> additionalInfo,
        Instant timestamp,
        ActionListener<String> listener
    ) {
        createInteraction(conversationId, input, promptTemplate, response, origin, additionalInfo, timestamp, listener, null, null);
    }

    /**
     * Add an interaction to this index, timestamped now. Return the id of the newly created interaction
     * @param conversationId The id of the converation this interaction belongs to
     * @param input the user (human) input into this interaction
     * @param promptTemplate the prompt template used for this interaction
     * @param response the GenAI response for this interaction
     * @param origin the name of the GenAI agent this interaction belongs to
     * @param additionalInfo additional information used to construct the LLM prompt
     * @param listener gets the id of the newly created interaction record
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
        createInteraction(conversationId, input, promptTemplate, response, origin, additionalInfo, Instant.now(), listener, null, null);
    }

    /**
     * Gets a list of interactions belonging to a conversation
     * @param conversationId the conversation to read from
     * @param from where to start in the reading
     * @param maxResults how many interactions to return
     * @param listener gets the list, sorted by recency, of interactions
     */
    public void getInteractions(String conversationId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            listener.onResponse(List.of());
            return;
        }
        ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
            if (access) {
                innerGetInteractions(conversationId, from, maxResults, listener);
            } else {
                String userstr = client
                    .threadPool()
                    .getThreadContext()
                    .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
                throw new OpenSearchStatusException(
                    "User [" + user + "] does not have access to memory " + conversationId,
                    RestStatus.UNAUTHORIZED
                );
            }
        }, e -> { listener.onFailure(e); });
        conversationMetaIndex.checkAccess(conversationId, accessListener);
    }

    @VisibleForTesting
    void innerGetInteractions(String conversationId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        SearchRequest request = Requests.searchRequest(INTERACTIONS_INDEX_NAME);

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

        request.source(searchSourceBuilder);
        request.source().from(from).size(maxResults);
        request.source().sort(ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD, SortOrder.ASC);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<Interaction>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                List<Interaction> result = new LinkedList<Interaction>();
                for (SearchHit hit : response.getHits()) {
                    result.add(Interaction.fromSearchHit(hit));
                }
                internalListener.onResponse(result);
                log.info("Successfully get the messages for memory {}", conversationId);
            }, e -> {
                internalListener.onFailure(e);
                log.error("Failed to get the messages for memory {}", conversationId);
            });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(INTERACTIONS_INDEX_NAME), ActionListener.wrap(r -> { client.search(request, al); }, e -> {
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Gets a list of interactions belonging to a conversation
     * @param interactionId the interaction to read from
     * @param from where to start in the reading
     * @param maxResults how many interactions to return
     * @param listener gets the list, sorted by recency, of interactions
     */
    public void getTraces(String interactionId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            listener.onResponse(List.of());
            return;
        }

        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<Interaction>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            GetRequest request = Requests.getRequest(INTERACTIONS_INDEX_NAME).id(interactionId);
            ActionListener<GetResponse> al = ActionListener.wrap(getResponse -> {
                // If the interaction doesn't exist, fail
                if (!(getResponse.isExists() && getResponse.getId().equals(interactionId))) {
                    throw new ResourceNotFoundException("Message [" + interactionId + "] not found");
                }
                Interaction interaction = Interaction.fromMap(interactionId, getResponse.getSourceAsMap());
                // checks if the user has permission to access the conversation that the interaction belongs to
                String conversationId = interaction.getConversationId();
                ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
                    if (access) {
                        innerGetTraces(interactionId, from, maxResults, listener);
                    } else {
                        String userstr = client
                            .threadPool()
                            .getThreadContext()
                            .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                        String user = User.parse(userstr) == null
                            ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS
                            : User.parse(userstr).getName();
                        throw new OpenSearchStatusException(
                            "User [" + user + "] does not have access to message " + interactionId,
                            RestStatus.UNAUTHORIZED
                        );
                    }
                }, e -> { listener.onFailure(e); });
                conversationMetaIndex.checkAccess(conversationId, accessListener);
            }, e -> { internalListener.onFailure(e); });
            client.admin().indices().refresh(Requests.refreshRequest(INTERACTIONS_INDEX_NAME), ActionListener.wrap(refreshResponse -> {
                client.get(request, ActionListener.runBefore(al, () -> threadContext.restore()));
            }, e -> {
                log.error("Failed to refresh message index during get message ", e);
                internalListener.onFailure(e);
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    @VisibleForTesting
    void innerGetTraces(String interactionId, int from, int maxResults, ActionListener<List<Interaction>> listener) {
        SearchRequest request = Requests.searchRequest(INTERACTIONS_INDEX_NAME);
        // Build the query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // Add the ExistsQueryBuilder for checking null values
        ExistsQueryBuilder existsQueryBuilder = QueryBuilders.existsQuery(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD);
        boolQueryBuilder.must(existsQueryBuilder);

        // Add the TermQueryBuilder for another field
        TermQueryBuilder termQueryBuilder = QueryBuilders
            .termQuery(ConversationalIndexConstants.PARENT_INTERACTIONS_ID_FIELD, interactionId);
        boolQueryBuilder.must(termQueryBuilder);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(boolQueryBuilder);

        request.source(searchSourceBuilder);
        request.source().from(from).size(maxResults);
        request.source().sort(ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD, SortOrder.ASC);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<Interaction>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<SearchResponse> al = ActionListener.wrap(response -> {
                List<Interaction> result = new LinkedList<Interaction>();
                for (SearchHit hit : response.getHits()) {
                    result.add(Interaction.fromSearchHit(hit));
                }
                internalListener.onResponse(result);
                log.info("Successfully get traces for the message {}", interactionId);
            }, e -> {
                internalListener.onFailure(e);
                log.error("Failed to get traces for the message {}", interactionId);
            });
            client.search(request, al);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Gets all interactions in a conversation, regardless of conversation size
     * @param conversationId conversation to get all interactions of
     * @param maxResults how many interactions to get per search query
     * @param listener receives the list of all interactions in the conversation
     */
    @VisibleForTesting
    void getAllInteractions(String conversationId, int maxResults, ActionListener<List<Interaction>> listener) {
        ActionListener<List<Interaction>> al = nextGetListener(conversationId, 0, maxResults, listener, new LinkedList<>());
        innerGetInteractions(conversationId, 0, maxResults, al);
    }

    /**
     * Recursively builds the list of interactions for getAllInteractions by returning an
     * ActionListener for handling the next search query
     * @param conversationId conversation to get interactions from
     * @param from where to start in this step
     * @param maxResults how many to get in this step
     * @param mainListener listener for the final result
     * @param result partially built list of interactions
     * @return an ActionListener to handle the next search query
     */
    @VisibleForTesting
    ActionListener<List<Interaction>> nextGetListener(
        String conversationId,
        int from,
        int maxResults,
        ActionListener<List<Interaction>> mainListener,
        List<Interaction> result
    ) {
        if (maxResults < 1) {
            mainListener.onFailure(new IllegalArgumentException("maxResults must be positive"));
            return null;
        }
        return ActionListener.wrap(interactions -> {
            result.addAll(interactions);
            if (interactions.size() < maxResults) {
                mainListener.onResponse(result);
            } else {
                ActionListener<List<Interaction>> al = nextGetListener(conversationId, from + maxResults, maxResults, mainListener, result);
                innerGetInteractions(conversationId, from + maxResults, maxResults, al);
            }
        }, e -> { mainListener.onFailure(e); });
    }

    /**
     * Deletes all interactions associated with a conversationId
     * Note this uses a bulk delete request (and tries to delete an entire conversation) so it may be heavyweight
     * @param conversationId the id of the conversation to delete from
     * @param listener gets whether the deletion was successful
     */
    public void deleteConversation(String conversationId, ActionListener<Boolean> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            listener.onResponse(true);
            return;
        }
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<List<Interaction>> searchListener = ActionListener.wrap(interactions -> {
                if (interactions.size() == 0) {
                    internalListener.onResponse(true);
                    return;
                }
                BulkRequest request = Requests.bulkRequest().setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
                for (Interaction interaction : interactions) {
                    DeleteRequest delRequest = Requests.deleteRequest(INTERACTIONS_INDEX_NAME).id(interaction.getId());
                    request.add(delRequest);
                }
                client
                    .bulk(request, ActionListener.wrap(bulkResponse -> { internalListener.onResponse(!bulkResponse.hasFailures()); }, e -> {
                        internalListener.onFailure(e);
                    }));
            }, e -> { internalListener.onFailure(e); });
            ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
                if (access) {
                    getAllInteractions(conversationId, resultsAtATime, searchListener);
                } else {
                    throw new OpenSearchStatusException(
                        "User [" + user + "] does not have access to memory " + conversationId,
                        RestStatus.UNAUTHORIZED
                    );
                }
            }, e -> { listener.onFailure(e); });
            conversationMetaIndex.checkAccess(conversationId, accessListener);
        } catch (Exception e) {
            log.error("Failure while deleting messages associated with memory id=" + conversationId, e);
            listener.onFailure(e);
        }
    }

    /**
     * Execute a search query over the interactions of a conversation by constructing a wrapper
     * boolean query around the original query, AND a term query over conversation id
     * @param conversationId the id of the conversation to query over
     * @param request the original search request
     * @param listener receives the search response from this query
     */
    public void searchInteractions(String conversationId, SearchRequest request, ActionListener<SearchResponse> listener) {
        conversationMetaIndex.checkAccess(conversationId, ActionListener.wrap(access -> {
            if (access) {
                try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<SearchResponse> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                    request.indices(INTERACTIONS_INDEX_NAME);
                    QueryBuilder originalQuery = request.source().query();
                    BoolQueryBuilder newQuery = new BoolQueryBuilder();
                    newQuery.must(originalQuery);
                    newQuery.must(new TermQueryBuilder(ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD, conversationId));
                    request.source().query(newQuery);
                    client
                        .admin()
                        .indices()
                        .refresh(Requests.refreshRequest(INTERACTIONS_INDEX_NAME), ActionListener.wrap(refreshResponse -> {
                            client.search(request, internalListener);
                        }, e -> {
                            log.error("Failed to refresh messages index during search messages ", e);
                            internalListener.onFailure(e);
                        }));
                } catch (Exception e) {
                    listener.onFailure(e);
                }
            } else {
                String userstr = client
                    .threadPool()
                    .getThreadContext()
                    .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
                throw new OpenSearchStatusException(
                    "User [" + user + "] does not have access to memory " + conversationId,
                    RestStatus.UNAUTHORIZED
                );
            }
        }, e -> { listener.onFailure(e); }));
    }

    /**
     * Get a single interaction
     * @param interactionId id of this interaction
     * @param listener receives the interaction
     */
    public void getInteraction(String interactionId, ActionListener<Interaction> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            listener
                .onFailure(
                    new IndexNotFoundException("cannot get message since the messages index does not exist", INTERACTIONS_INDEX_NAME)
                );
            return;
        }
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Interaction> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            GetRequest request = Requests.getRequest(INTERACTIONS_INDEX_NAME).id(interactionId);
            ActionListener<GetResponse> al = ActionListener.wrap(getResponse -> {
                // If the interaction doesn't exist, fail
                if (!(getResponse.isExists() && getResponse.getId().equals(interactionId))) {
                    throw new ResourceNotFoundException("Message [" + interactionId + "] not found");
                }
                Interaction interaction = Interaction.fromMap(interactionId, getResponse.getSourceAsMap());
                // checks if the user has permission to access the conversation that the interaction belongs to
                checkInteractionPermission(interactionId, interaction, internalListener);
            }, e -> { internalListener.onFailure(e); });
            client.admin().indices().refresh(Requests.refreshRequest(INTERACTIONS_INDEX_NAME), ActionListener.wrap(refreshResponse -> {
                client.get(request, ActionListener.runBefore(al, () -> threadContext.restore()));
            }, e -> {
                log.error("Failed to refresh message index during get message ", e);
                internalListener.onFailure(e);
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Update interaction in the index
     * @param interactionId the interaction id that needs update
     * @param updateRequest original update request
     * @param listener receives the update response for the wrapped query
     */
    public void updateInteraction(String interactionId, UpdateRequest updateRequest, ActionListener<UpdateResponse> listener) {
        if (!clusterService.state().metadata().hasIndex(INTERACTIONS_INDEX_NAME)) {
            listener
                .onFailure(
                    new IndexNotFoundException("cannot update message since the message index does not exist", INTERACTIONS_INDEX_NAME)
                );
            return;
        }

        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            GetRequest request = Requests.getRequest(INTERACTIONS_INDEX_NAME).id(interactionId);
            ActionListener<GetResponse> al = ActionListener.wrap(getResponse -> {
                // If the interaction doesn't exist, fail
                if (!(getResponse.isExists() && getResponse.getId().equals(interactionId))) {
                    throw new ResourceNotFoundException("message [" + interactionId + "] not found");
                }
                Interaction interaction = Interaction.fromMap(interactionId, getResponse.getSourceAsMap());
                // checks if the user has permission to access the conversation that the interaction belongs to
                String conversationId = interaction.getConversationId();
                ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
                    if (access) {
                        innerUpdateInteraction(updateRequest, internalListener);
                    } else {
                        String userstr = client
                            .threadPool()
                            .getThreadContext()
                            .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                        String user = User.parse(userstr) == null
                            ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS
                            : User.parse(userstr).getName();
                        throw new OpenSearchStatusException(
                            "User [" + user + "] does not have access to message " + interactionId,
                            RestStatus.UNAUTHORIZED
                        );
                    }
                }, e -> { listener.onFailure(e); });
                conversationMetaIndex.checkAccess(conversationId, accessListener);
            }, e -> { internalListener.onFailure(e); });
            client.admin().indices().refresh(Requests.refreshRequest(INTERACTIONS_INDEX_NAME), ActionListener.wrap(refreshResponse -> {
                client.get(request, ActionListener.runBefore(al, () -> threadContext.restore()));
            }, e -> {
                log.error("Failed to refresh messages index during get message ", e);
                internalListener.onFailure(e);
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    private void innerUpdateInteraction(UpdateRequest updateRequest, ActionListener<UpdateResponse> listener) {
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<UpdateResponse> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            client.update(updateRequest, internalListener);
        } catch (Exception e) {
            log.error("Failed to update message. Details {}:", e);
            listener.onFailure(e);
        }
    }

    private void checkInteractionPermission(String interactionId, Interaction interaction, ActionListener<Interaction> internalListener) {
        String conversationId = interaction.getConversationId();
        ActionListener<Boolean> accessListener = ActionListener.wrap(access -> {
            if (access) {
                internalListener.onResponse(interaction);
                log.info("Successfully get the message : {}", interactionId);
            } else {
                String userstr = client
                    .threadPool()
                    .getThreadContext()
                    .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
                throw new OpenSearchStatusException(
                    "User [" + user + "] does not have access to message " + interactionId,
                    RestStatus.UNAUTHORIZED
                );
            }
        }, e -> { internalListener.onFailure(e); });
        conversationMetaIndex.checkAccess(conversationId, accessListener);
    }
}
