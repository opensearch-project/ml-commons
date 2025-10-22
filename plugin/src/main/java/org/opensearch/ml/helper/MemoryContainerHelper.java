/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.BACKEND_ROLES_FIELD;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.DEFAULT_LLM_RESULT_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_RESULT_PATH_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import java.util.List;

import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.clustermanager.AcknowledgedResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.NestedQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

/**
 * Helper class for memory container operations shared across transport actions
 */
@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MemoryContainerHelper {

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public MemoryContainerHelper(Client client, SdkClient sdkClient, NamedXContentRegistry xContentRegistry) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
    }

    /**
     * Get memory container by ID
     * 
     * @param memoryContainerId the container ID
     * @param listener action listener for the result
     */
    public void getMemoryContainer(String memoryContainerId, ActionListener<MLMemoryContainer> listener) {
        getMemoryContainer(memoryContainerId, null, listener);
    }

    /**
     * Get memory container by ID with tenant support
     * 
     * @param memoryContainerId the container ID
     * @param tenantId the tenant ID (optional)
     * @param listener action listener for the result
     */
    public void getMemoryContainer(String memoryContainerId, String tenantId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest.Builder requestBuilder = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .fetchSourceContext(fetchSourceContext);

        if (tenantId != null) {
            requestBuilder.tenantId(tenantId);
        }

        GetDataObjectRequest getDataObjectRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<MLMemoryContainer> wrappedListener = ActionListener.runBefore(listener, context::restore);

            sdkClient.getDataObjectAsync(getDataObjectRequest).whenComplete((r, throwable) -> {
                if (throwable != null) {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
                    if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                        wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                    } else {
                        wrappedListener.onFailure(cause);
                    }
                } else {
                    try {
                        if (r.getResponse() != null && r.getResponse().isExists()) {
                            try (
                                XContentParser parser = jsonXContent
                                    .createParser(xContentRegistry, LoggingDeprecationHandler.INSTANCE, r.getResponse().getSourceAsString())
                            ) {
                                ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                                MLMemoryContainer container = MLMemoryContainer.parse(parser);
                                wrappedListener.onResponse(container);
                            }
                        } else {
                            wrappedListener.onFailure(new OpenSearchStatusException("Memory container not found", RestStatus.NOT_FOUND));
                        }
                    } catch (Exception e) {
                        wrappedListener.onFailure(e);
                    }
                }
            });
        }
    }

    /**
     * Check if user has access to memory container
     * 
     * @param user the user to check
     * @param mlMemoryContainer the container to check access for
     * @return true if user has access, false otherwise
     */
    public boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        User owner = mlMemoryContainer.getOwner();
        if (owner != null && owner.getName() != null && owner.getName().equals(user.getName())) {
            return true;
        }

        List<String> allowedBackendRoles = mlMemoryContainer.getBackendRoles();
        // Check if user has any of the allowed backend roles
        if (allowedBackendRoles != null && !allowedBackendRoles.isEmpty() && user.getBackendRoles() != null) {
            return allowedBackendRoles.stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }

    public boolean checkMemoryAccess(User user, String ownerId) {
        // If security is disabled (user is null), allow access
        if (user == null) {
            return true;
        }

        // If user is admin (has all_access role), allow access
        if (user.getRoles() != null && user.getRoles().contains("all_access")) {
            return true;
        }

        // Check if user is the owner
        String userName = user.getName();
        if (userName.equals(ownerId)) {
            return true;
        }
        return false;
    }

    /**
     * Get memory index name from container
     *
     * @param container the memory container
     * @return the memory index name or null if not configured
     */
    public String getMemoryIndexName(MLMemoryContainer container, MemoryType memoryType) {
        MemoryConfiguration config = container.getConfiguration();
        if (config != null && memoryType != null) {
            return config.getIndexName(memoryType);
        }
        return null;
    }

    public void getData(MemoryConfiguration configuration, GetRequest getRequest, ActionListener<GetResponse> listener) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.get(getRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.get(getRequest, listener);
        }
    }

    public void searchData(
        MemoryConfiguration configuration,
        SearchDataObjectRequest searchRequest,
        ActionListener<SearchResponse> listener
    ) {
        try {
            if (configuration.isUseSystemIndex()) {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
                    final ActionListener<SearchResponse> doubleWrappedListener = ActionListener
                        .wrap(wrappedListener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, wrappedListener));

                    sdkClient.searchDataObjectAsync(searchRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(doubleWrappedListener));
                }
            } else {
                final ActionListener<SearchResponse> doubleWrappedListener = ActionListener
                    .wrap(listener::onResponse, e -> wrapListenerToHandleSearchIndexNotFound(e, listener));

                sdkClient.searchDataObjectAsync(searchRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(doubleWrappedListener));
            }
        } catch (Exception e) {
            log.error("Failed to search data", e);
            listener.onFailure(e);
        }
    }

    public void indexData(MemoryConfiguration configuration, IndexRequest indexRequest, ActionListener<IndexResponse> listener) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.index(indexRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.index(indexRequest, listener);
        }
    }

    public void updateData(MemoryConfiguration configuration, UpdateRequest updateRequest, ActionListener<UpdateResponse> listener) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.update(updateRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.update(updateRequest, listener);
        }
    }

    public void deleteData(MemoryConfiguration configuration, DeleteRequest deleteRequest, ActionListener<DeleteResponse> listener) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.delete(deleteRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.delete(deleteRequest, listener);
        }
    }

    public void deleteIndex(
        MemoryConfiguration configuration,
        DeleteIndexRequest deleteIndexRequest,
        ActionListener<AcknowledgedResponse> listener
    ) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.admin().indices().delete(deleteIndexRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.admin().indices().delete(deleteIndexRequest, listener);
        }
    }

    public void bulkIngestData(MemoryConfiguration configuration, BulkRequest bulkRequest, ActionListener<BulkResponse> listener) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.bulk(bulkRequest, ActionListener.runBefore(listener, context::restore));
            }
        } else {
            client.bulk(bulkRequest, listener);
        }
    }

    /**
     * Execute delete by query with proper system index handling
     *
     * @param configuration memory configuration
     * @param deleteByQueryRequest the delete by query request
     * @param listener action listener for the result
     */
    public void deleteDataByQuery(
        MemoryConfiguration configuration,
        DeleteByQueryRequest deleteByQueryRequest,
        ActionListener<BulkByScrollResponse> listener
    ) {
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client.execute(DeleteByQueryAction.INSTANCE, deleteByQueryRequest, ActionListener.runBefore(listener, context::restore));
            } catch (Exception e) {
                log.error("Failed to execute delete by query on system index", e);
                listener.onFailure(e);
            }
        } else {
            client.execute(DeleteByQueryAction.INSTANCE, deleteByQueryRequest, listener);
        }
    }

    public boolean isAdminUser(User user) {
        return user != null && (!CollectionUtils.isEmpty(user.getRoles()) && user.getRoles().contains("all_access"));
    }

    /**
     * Generic helper method to apply a filter to a SearchSourceBuilder.
     * Handles three cases:
     * 1. If no existing query, use the filter as the query
     * 2. If existing query is BoolQueryBuilder, add filter to it
     * 3. Otherwise, wrap existing query in a BoolQueryBuilder with the filter
     *
     * @param searchSourceBuilder The search source builder to modify
     * @param filterQuery The filter query to apply
     * @return The modified search source builder
     */
    private SearchSourceBuilder applyFilterToSearchSource(SearchSourceBuilder searchSourceBuilder, QueryBuilder filterQuery) {
        QueryBuilder query = searchSourceBuilder.query();
        if (query == null) {
            searchSourceBuilder.query(filterQuery);
        } else if (query instanceof BoolQueryBuilder) {
            ((BoolQueryBuilder) query).filter(filterQuery);
        } else {
            BoolQueryBuilder rewriteQuery = new BoolQueryBuilder();
            rewriteQuery.must(query);
            rewriteQuery.filter(filterQuery);
            searchSourceBuilder.query(rewriteQuery);
        }
        return searchSourceBuilder;
    }

    public SearchSourceBuilder addUserBackendRolesFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        boolQueryBuilder.should(QueryBuilders.termsQuery(BACKEND_ROLES_FIELD + ".keyword", user.getBackendRoles()));

        TermQueryBuilder ownerNameTermQuery = QueryBuilders.termQuery("owner.name.keyword", user.getName());
        NestedQueryBuilder nestedOwnerQuery = QueryBuilders.nestedQuery(OWNER_FIELD, ownerNameTermQuery, ScoreMode.None);
        boolQueryBuilder.should(nestedOwnerQuery);

        return applyFilterToSearchSource(searchSourceBuilder, boolQueryBuilder);
    }

    public SearchSourceBuilder addOwnerIdFilter(User user, SearchSourceBuilder searchSourceBuilder) {
        BoolQueryBuilder boolQueryBuilder = new BoolQueryBuilder();
        if (user != null) {
            boolQueryBuilder.should(QueryBuilders.termsQuery(OWNER_ID_FIELD, user.getName()));
        }
        return applyFilterToSearchSource(searchSourceBuilder, boolQueryBuilder);
    }

    /**
     * Add owner ID filter to a QueryBuilder for non-admin users.
     * For admin users or when security is disabled, returns the original query.
     *
     * @param user The current user (can be null if security is disabled)
     * @param query The original query to filter
     * @return The filtered query with owner restrictions for non-admin users
     */
    public QueryBuilder addOwnerIdFilter(User user, QueryBuilder query) {
        // If security is disabled or user is admin, use the original query
        if (user == null || isAdminUser(user)) {
            return query;
        }

        // For non-admin users, add owner filter
        BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
        filteredQuery.must(query);
        filteredQuery.filter(QueryBuilders.termQuery(OWNER_ID_FIELD, user.getName()));

        return filteredQuery;
    }

    /**
     * Add memory container ID filter to a SearchSourceBuilder.
     * This ensures that searches only return memories from the specified container,
     * preventing cross-container access when multiple containers share the same index prefix.
     *
     * @param containerId The memory container ID to filter by
     * @param searchSourceBuilder The search source builder to update
     * @return The updated search source builder with container ID filter applied
     */
    public SearchSourceBuilder addContainerIdFilter(String containerId, SearchSourceBuilder searchSourceBuilder) {
        if (containerId == null || containerId.isBlank()) {
            return searchSourceBuilder;
        }
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));

        return applyFilterToSearchSource(searchSourceBuilder, boolQueryBuilder);
    }

    /**
     * Add memory container ID filter to a QueryBuilder.
     * This ensures that queries only match memories from the specified container,
     * preventing cross-container access when multiple containers share the same index prefix.
     *
     * @param containerId The memory container ID to filter by
     * @param query The original query to filter
     * @return The filtered query with container ID restriction
     */
    public QueryBuilder addContainerIdFilter(String containerId, QueryBuilder query) {
        if (containerId == null || containerId.isBlank()) {
            return query;
        }

        BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
        filteredQuery.must(query);
        filteredQuery.filter(QueryBuilders.termQuery(MEMORY_CONTAINER_ID_FIELD, containerId));

        return filteredQuery;
    }

    public String getOwnerId(User user) {
        return user != null ? user.getName() : null;
    }

    /**
     * Count memory containers with the specified index prefix
     *
     * @param indexPrefix the index prefix to search for
     * @param tenantId the tenant ID (optional)
     * @param listener action listener returning the count
     */
    public void countContainersWithPrefix(String indexPrefix, String tenantId, ActionListener<Long> listener) {
        if (indexPrefix == null || indexPrefix.isBlank()) {
            listener.onResponse(0L);
            return;
        }

        // Build search query for containers with matching index prefix
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.termQuery("configuration.index_prefix", indexPrefix));
        searchSourceBuilder.size(0); // We only need the total count
        searchSourceBuilder.trackTotalHits(true);

        SearchDataObjectRequest.Builder requestBuilder = SearchDataObjectRequest
            .builder()
            .indices(ML_MEMORY_CONTAINER_INDEX)
            .searchSourceBuilder(searchSourceBuilder);

        if (tenantId != null) {
            requestBuilder.tenantId(tenantId);
        }

        SearchDataObjectRequest searchRequest = requestBuilder.build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<SearchResponse> wrappedListener = ActionListener.runBefore(ActionListener.wrap(response -> {
                long totalHits = response.getHits().getTotalHits().value();
                listener.onResponse(totalHits);
            }, e -> {
                log.error("Failed to count containers with prefix: " + indexPrefix, e);
                listener.onFailure(e);
            }), context::restore);

            sdkClient.searchDataObjectAsync(searchRequest).whenComplete(SdkClientUtils.wrapSearchCompletion(wrappedListener));
        } catch (Exception e) {
            log.error("Failed to search for containers with prefix: " + indexPrefix, e);
            listener.onFailure(e);
        }
    }

    /**
     * Get the LLM result path from strategy or memory configuration.
     * Priority order:
     * 1. Strategy config's llm_result_path
     * 2. Memory config parameters' llm_result_path
     * 3. Default path
     *
     * @param strategy the memory strategy (can be null)
     * @param memoryConfig the memory configuration (can be null)
     * @return the LLM result path, never null
     */
    public String getLlmResultPath(MemoryStrategy strategy, MemoryConfiguration memoryConfig) {
        // Try to get from strategy config first
        if (strategy != null && strategy.getStrategyConfig() != null) {
            Object strategyPath = strategy.getStrategyConfig().get(LLM_RESULT_PATH_FIELD);
            if (strategyPath != null) {
                return strategyPath.toString();
            }
        }

        // Fall back to memory config parameters
        if (memoryConfig != null && memoryConfig.getParameters() != null) {
            Object configPath = memoryConfig.getParameters().get(LLM_RESULT_PATH_FIELD);
            if (configPath != null) {
                return configPath.toString();
            }
        }

        // Use default if nothing found
        return DEFAULT_LLM_RESULT_PATH;
    }
}
