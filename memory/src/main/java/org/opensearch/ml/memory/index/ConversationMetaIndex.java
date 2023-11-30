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

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;

import org.opensearch.OpenSearchSecurityException;
import org.opensearch.OpenSearchWrapperException;
import org.opensearch.ResourceAlreadyExistsException;
import org.opensearch.ResourceNotFoundException;
import org.opensearch.action.DocWriteResponse.Result;
import org.opensearch.action.admin.indices.create.CreateIndexRequest;
import org.opensearch.action.admin.indices.create.CreateIndexResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.conversation.ActionConstants;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.search.SearchHit;
import org.opensearch.search.sort.SortOrder;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Class for handling the conversational metadata index
 */
@Log4j2
@AllArgsConstructor
public class ConversationMetaIndex {

    private Client client;
    private ClusterService clusterService;
    private static final String indexName = ConversationalIndexConstants.META_INDEX_NAME;

    /**
     * Creates the conversational meta index if it doesn't already exist
     * @param listener listener to wait for this to finish
     */
    public void initConversationMetaIndexIfAbsent(ActionListener<Boolean> listener) {
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            log.debug("No conversational meta index found. Adding it");
            CreateIndexRequest request = Requests.createIndexRequest(indexName).mapping(ConversationalIndexConstants.META_MAPPING);
            try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                ActionListener<CreateIndexResponse> al = ActionListener.wrap(createIndexResponse -> {
                    if (createIndexResponse.equals(new CreateIndexResponse(true, true, indexName))) {
                        log.info("created index [" + indexName + "]");
                        internalListener.onResponse(true);
                    } else {
                        internalListener.onResponse(false);
                    }
                }, e -> {
                    if (e instanceof ResourceAlreadyExistsException
                        || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                        internalListener.onResponse(true);
                    } else {
                        log.error("failed to create index [" + indexName + "]", e);
                        internalListener.onFailure(e);
                    }
                });
                client.admin().indices().create(request, al);
            } catch (Exception e) {
                if (e instanceof ResourceAlreadyExistsException
                    || (e instanceof OpenSearchWrapperException && e.getCause() instanceof ResourceAlreadyExistsException)) {
                    listener.onResponse(true);
                } else {
                    log.error("failed to create index [" + indexName + "]", e);
                    listener.onFailure(e);
                }
            }
        } else {
            listener.onResponse(true);
        }
    }

    /**
     * Adds a new conversation with the specified name to the index
     * @param name user-specified name of the conversation to be added
     * @param applicationType the application type that creates this conversation
     * @param listener listener to wait for this to finish
     */
    public void createConversation(String name, String applicationType, ActionListener<String> listener) {
        initConversationMetaIndexIfAbsent(ActionListener.wrap(indexExists -> {
            if (indexExists) {
                String userstr = client
                    .threadPool()
                    .getThreadContext()
                    .getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
                IndexRequest request = Requests
                    .indexRequest(indexName)
                    .source(
                        ConversationalIndexConstants.META_CREATED_FIELD,
                        Instant.now(),
                        ConversationalIndexConstants.META_UPDATED_FIELD,
                        Instant.now(),
                        ConversationalIndexConstants.META_NAME_FIELD,
                        name,
                        ConversationalIndexConstants.USER_FIELD,
                        userstr == null ? null : User.parse(userstr).getName(),
                        ConversationalIndexConstants.APPLICATION_TYPE_FIELD,
                        applicationType
                    );
                try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<String> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                    ActionListener<IndexResponse> al = ActionListener.wrap(resp -> {
                        if (resp.status() == RestStatus.CREATED) {
                            internalListener.onResponse(resp.getId());
                        } else {
                            internalListener.onFailure(new IOException("failed to create conversation"));
                        }
                    }, e -> {
                        log.error("Failed to create conversation", e);
                        internalListener.onFailure(e);
                    });
                    client.index(request, al);
                } catch (Exception e) {
                    log.error("Failed to create conversation", e);
                    listener.onFailure(e);
                }
            } else {
                listener.onFailure(new IOException("Failed to add conversation due to missing index"));
            }
        }, e -> { listener.onFailure(e); }));
    }

    /**
     * Adds a new conversation named ""
     * @param listener listener to wait for this to finish
     */
    public void createConversation(ActionListener<String> listener) {
        createConversation("", "", listener);
    }

    /**
     * Adds a new conversation named ""
     * @param name user-specified name of the conversation to be added
     * @param listener listener to wait for this to finish
     */
    public void createConversation(String name, ActionListener<String> listener) {
        createConversation(name, "", listener);
    }

    /**
     * list size conversations in the index
     * @param from where to start listing from
     * @param maxResults how many conversations to list
     * @param listener gets the list of conversation metadata objects in the index
     */
    public void getConversations(int from, int maxResults, ActionListener<List<ConversationMeta>> listener) {
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            listener.onResponse(List.of());
        }
        SearchRequest request = Requests.searchRequest(indexName);
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        QueryBuilder queryBuilder;
        if (userstr == null)
            queryBuilder = new MatchAllQueryBuilder();
        else
            queryBuilder = new TermQueryBuilder(ConversationalIndexConstants.USER_FIELD, User.parse(userstr).getName());
        request.source().query(queryBuilder);
        request.source().from(from).size(maxResults);
        request.source().sort(ConversationalIndexConstants.META_UPDATED_FIELD, SortOrder.DESC);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<List<ConversationMeta>> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            ActionListener<SearchResponse> al = ActionListener.wrap(searchResponse -> {
                List<ConversationMeta> result = new LinkedList<ConversationMeta>();
                for (SearchHit hit : searchResponse.getHits()) {
                    result.add(ConversationMeta.fromSearchHit(hit));
                }
                internalListener.onResponse(result);
            }, e -> {
                log.error("Failed to retrieve conversations", e);
                internalListener.onFailure(e);
            });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(indexName), ActionListener.wrap(refreshResponse -> { client.search(request, al); }, e -> {
                    log.error("Failed to retrieve conversations during refresh", e);
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            log.error("Failed to retrieve conversations", e);
            listener.onFailure(e);
        }
    }

    /**
     * list size conversations in the index
     * @param maxResults how many conversations to list
     * @param listener gets the list of conversation metadata objects in the index
     */
    public void getConversations(int maxResults, ActionListener<List<ConversationMeta>> listener) {
        getConversations(0, maxResults, listener);
    }

    /**
     * Deletes a conversation from the conversation metadata index
     * @param conversationId id of the conversation to delete
     * @param listener gets whether the deletion was successful
     */
    public void deleteConversation(String conversationId, ActionListener<Boolean> listener) {
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            listener.onResponse(true);
        }
        DeleteRequest delRequest = Requests.deleteRequest(indexName).id(conversationId);
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
        this.checkAccess(conversationId, ActionListener.wrap(access -> {
            if (access) {
                try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
                    // When we get the delete response, do this:
                    ActionListener<DeleteResponse> al = ActionListener.wrap(deleteResponse -> {
                        if (deleteResponse.getResult() == Result.DELETED) {
                            internalListener.onResponse(true);
                        } else if (deleteResponse.status() == RestStatus.NOT_FOUND) {
                            internalListener.onResponse(true);
                        } else {
                            internalListener.onResponse(false);
                        }
                    }, e -> {
                        log.error("Failure deleting conversation " + conversationId, e);
                        internalListener.onFailure(e);
                    });
                    client.delete(delRequest, al);
                } catch (Exception e) {
                    log.error("Failed deleting conversation with id=" + conversationId, e);
                    listener.onFailure(e);
                }
            } else {
                throw new OpenSearchSecurityException("User [" + user + "] does not have access to conversation " + conversationId);
            }
        }, e -> { listener.onFailure(e); }));
    }

    /**
     * Checks whether the current requesting user has permission to see this conversation
     * @param conversationId the conversation to check
     * @param listener receives whether access should be granted
     */
    public void checkAccess(String conversationId, ActionListener<Boolean> listener) {
        // If the index doesn't exist, you have permission. Just won't get you anywhere
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            listener.onResponse(true);
            return;
        }
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<Boolean> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            GetRequest getRequest = Requests.getRequest(indexName).id(conversationId);
            ActionListener<GetResponse> al = ActionListener.wrap(getResponse -> {
                // If the conversation doesn't exist, fail
                if (!(getResponse.isExists() && getResponse.getId().equals(conversationId))) {
                    throw new ResourceNotFoundException("Conversation [" + conversationId + "] not found");
                }
                // If security is off - User doesn't exist - you have permission
                if (userstr == null || User.parse(userstr) == null) {
                    internalListener.onResponse(true);
                    return;
                }
                ConversationMeta conversation = ConversationMeta.fromMap(conversationId, getResponse.getSourceAsMap());
                String user = User.parse(userstr).getName();
                // If you're not the owner of this conversation, you do not have permission
                if (!user.equals(conversation.getUser())) {
                    internalListener.onResponse(false);
                    return;
                }
                internalListener.onResponse(true);
            }, e -> { internalListener.onFailure(e); });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(indexName), ActionListener.wrap(refreshResponse -> { client.get(getRequest, al); }, e -> {
                    log.error("Failed to refresh conversations index during check access ", e);
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Search over the conversations in the index by wrapping the original search request
     * If security is enabled, add a {"term": {"user": username}} to the wrapper must clause
     * @param request original search request
     * @param listener receives the search response for the wrapped query
     */
    public void searchConversations(SearchRequest request, ActionListener<SearchResponse> listener) {
        request.indices(indexName);
        QueryBuilder originalQuery = request.source().query();
        BoolQueryBuilder newQuery = new BoolQueryBuilder();
        newQuery.must(originalQuery);
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        if (userstr != null) {
            String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
            newQuery.must(new TermQueryBuilder(ConversationalIndexConstants.USER_FIELD, user));
        }
        request.source().query(newQuery);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            client.admin().indices().refresh(Requests.refreshRequest(indexName), ActionListener.wrap(refreshResponse -> {
                client.search(request, internalListener);
            }, e -> {
                log.error("Failed to refresh conversations index during search conversations ", e);
                internalListener.onFailure(e);
            }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }

    /**
     * Update conversations in the index
     * @param updateRequest original update request
     * @param listener receives the update response for the wrapped query
     */
    public void updateConversation(UpdateRequest updateRequest, ActionListener<UpdateResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, listener);
        } catch (Exception e) {
            log.error("Failed to update Conversation. Details {}:", e);
            listener.onFailure(e);
        }
    }

    /**
     * Get a single ConversationMeta object
     * @param conversationId id of the conversation to get
     * @param listener receives the conversationMeta object
     */
    public void getConversation(String conversationId, ActionListener<ConversationMeta> listener) {
        if (!clusterService.state().metadata().hasIndex(indexName)) {
            listener
                .onFailure(new IndexNotFoundException("cannot get conversation since the conversation index does not exist", indexName));
            return;
        }
        String userstr = client.threadPool().getThreadContext().getTransient(ConfigConstants.OPENSEARCH_SECURITY_USER_INFO_THREAD_CONTEXT);
        try (ThreadContext.StoredContext threadContext = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<ConversationMeta> internalListener = ActionListener.runBefore(listener, () -> threadContext.restore());
            GetRequest request = Requests.getRequest(indexName).id(conversationId);
            ActionListener<GetResponse> al = ActionListener.wrap(getResponse -> {
                // If the conversation doesn't exist, fail
                if (!(getResponse.isExists() && getResponse.getId().equals(conversationId))) {
                    throw new ResourceNotFoundException("Conversation [" + conversationId + "] not found");
                }
                ConversationMeta conversation = ConversationMeta.fromMap(conversationId, getResponse.getSourceAsMap());
                // If no security, return conversation
                if (userstr == null || User.parse(userstr) == null) {
                    internalListener.onResponse(conversation);
                    return;
                }
                // If security and correct user, return conversation
                String user = User.parse(userstr) == null ? ActionConstants.DEFAULT_USERNAME_FOR_ERRORS : User.parse(userstr).getName();
                if (user.equals(conversation.getUser())) {
                    internalListener.onResponse(conversation);
                    return;
                }
                // Otherwise you don't have permission
                internalListener
                    .onFailure(
                        new OpenSearchSecurityException("User [" + user + "] does not have access to conversation " + conversationId)
                    );
            }, e -> { internalListener.onFailure(e); });
            client
                .admin()
                .indices()
                .refresh(Requests.refreshRequest(indexName), ActionListener.wrap(refreshResponse -> { client.get(request, al); }, e -> {
                    log.error("Failed to refresh conversations index during get conversation ", e);
                    internalListener.onFailure(e);
                }));
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
