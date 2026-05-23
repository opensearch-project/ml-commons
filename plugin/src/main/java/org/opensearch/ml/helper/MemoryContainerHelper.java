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
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.BEDROCK_STRUCTURED_OUTPUT_RESULT_PATH;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACTS_EXTRACTION_BEDROCK_CONVERSE_TOOL_CONFIG_JSON;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACTS_EXTRACTION_COHERE_RESPONSE_FORMAT_JSON;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACTS_EXTRACTION_GEMINI_GENERATION_CONFIG_JSON;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.FACTS_EXTRACTION_OPENAI_RESPONSE_FORMAT_JSON;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.LLM_RESULT_PATH_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEMORY_CONTAINER_ID_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_FIELD;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleSearchIndexNotFound;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.model.MLModelManager;
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

    // URL host/path tokens used to identify LLM providers for structured output schema selection.
    // Each constant is a single host label or path segment — matched with exact equality after
    // splitting on '.' (host) or '/' (path), so "my-openai-proxy.internal" does not match "openai".
    // Keeping them as named constants makes the precedence order in schemaForUrl explicit.
    private static final String URL_TOKEN_GOOGLEAPIS = "googleapis";
    private static final String URL_TOKEN_COHERE = "cohere";
    private static final String URL_TOKEN_V2_PATH_SEGMENT = "v2";
    private static final String URL_TOKEN_OPENAI = "openai";
    private static final String URL_TOKEN_DEEPSEEK = "deepseek";
    // Multi-segment path — used with path.contains() on a query-stripped path, which is safe.
    private static final String URL_TOKEN_OPENAI_COMPAT_PATH = "/v1/chat/completions";
    private static final String URL_TOKEN_AMAZONAWS = "amazonaws";
    private static final String URL_TOKEN_CONVERSE_SEGMENT = "converse";

    // Captures (1) host and (2) path from a URL, stopping before '?' or '#'.
    // Handles connector template URLs such as https://${parameters.endpoint}/openai/...
    // where the host may be a placeholder — in that case path matching takes over.
    private static final Pattern URL_PARTS = Pattern.compile("^[^:]+://([^/?#]*)(/?[^?#]*)");

    Client client;
    SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;
    MLModelManager modelManager;

    @Inject
    public MemoryContainerHelper(Client client, SdkClient sdkClient, NamedXContentRegistry xContentRegistry, MLModelManager modelManager) {
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.modelManager = modelManager;
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
        log.debug("Fetching memory container with ID: {} for tenant: {}", memoryContainerId, tenantId);
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
        boolQueryBuilder.should(QueryBuilders.termsQuery(OWNER_ID_FIELD, user.getName()));

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
     * Resolves the structured output injection parameters for the given model asynchronously.
     * Looks up the model's connector, checks its PREDICT action's {@code supports_structured_output}
     * flag, then derives the provider from the connector URL to select the correct injection
     * parameter and schema constant. Returns an empty map if the connector does not support native
     * structured output or if the lookup fails; callers should fall back to prompt enforcement.
     */
    public void getStructuredOutputParameters(String modelId, ActionListener<Map<String, String>> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            modelManager.getModel(modelId, ActionListener.runBefore(ActionListener.wrap(mlModel -> {
                Connector connector = mlModel.getConnector();
                if (connector != null) {
                    listener.onResponse(schemaForConnector(connector));
                } else if (mlModel.getConnectorId() != null) {
                    // getConnector stashes its own ThreadContext internally, so this secondary
                    // async call is safe even though the outer stashed context has already been
                    // restored by the runBefore above.
                    modelManager
                        .getConnector(
                            mlModel.getConnectorId(),
                            null,
                            ActionListener.wrap(c -> listener.onResponse(schemaForConnector(c)), e -> {
                                log.warn("Failed to fetch connector {} for structured output detection", mlModel.getConnectorId(), e);
                                listener.onResponse(Map.of());
                            })
                        );
                } else {
                    listener.onResponse(Map.of());
                }
            }, e -> {
                log.warn("Failed to fetch model {} for structured output detection, falling back to prompt enforcement", modelId, e);
                listener.onResponse(Map.of());
            }), context::restore));
        }
    }

    private Map<String, String> schemaForConnector(Connector connector) {
        if (connector.getActions() == null) {
            return Map.of();
        }
        // first matching PREDICT action wins; assumes one PREDICT per connector for fact extraction
        return connector
            .getActions()
            .stream()
            .filter(a -> ConnectorAction.ActionType.PREDICT.equals(a.getActionType()) && a.isSupportsStructuredOutput())
            .findFirst()
            .map(a -> schemaForUrl(a.getUrl()))
            .orElse(Map.of());
    }

    private Map<String, String> schemaForUrl(String url) {
        if (url == null) {
            return Map.of();
        }
        Matcher m = URL_PARTS.matcher(url);
        if (!m.find()) {
            return Map.of();
        }
        // Match against host and path only — never against query string, credentials, or fragment —
        // so a query parameter or anchor containing a provider name can't cause a false match.
        // hostHasSegment / pathHasSegment use exact label/segment equality after splitting on
        // '.' or '/', so "my-openai-proxy.internal" does not match the "openai" token.
        String host = m.group(1).toLowerCase(Locale.ROOT);
        String path = m.group(2).toLowerCase(Locale.ROOT);

        // Bedrock Converse: toolConfig forces the model into tool-use mode; the response arrives at
        // content[0].toolUse.input rather than content[0].text, so a result path override is returned
        // alongside the schema. MemoryProcessingService strips the override key before the predict call.
        if (hostHasSegment(host, URL_TOKEN_AMAZONAWS) && pathHasSegment(path, URL_TOKEN_CONVERSE_SEGMENT)) {
            return Map.of(
                "_toolConfig_json", FACTS_EXTRACTION_BEDROCK_CONVERSE_TOOL_CONFIG_JSON,
                "_structured_output_result_path", BEDROCK_STRUCTURED_OUTPUT_RESULT_PATH
            );
        }
        // Anthropic direct API is not yet supported — see follow-up issues.
        if (hostHasSegment(host, URL_TOKEN_GOOGLEAPIS)) {
            return Map.of("_generationConfig_additions_json", FACTS_EXTRACTION_GEMINI_GENERATION_CONFIG_JSON);
        }
        // Cohere structured output (json_schema type) requires the v2 Chat API (/v2/chat).
        // The legacy /v1/chat endpoint only supports json_object and ignores json_schema.
        if (hostHasSegment(host, URL_TOKEN_COHERE) && pathHasSegment(path, URL_TOKEN_V2_PATH_SEGMENT)) {
            return Map.of("_response_format_json", FACTS_EXTRACTION_COHERE_RESPONSE_FORMAT_JSON);
        }
        // hostHasSegment(host, URL_TOKEN_OPENAI) catches api.openai.com and similar.
        // pathHasSegment(path, URL_TOKEN_OPENAI) is specifically for Azure, whose connector URL uses
        // a template host (${parameters.endpoint}), so "openai" only appears in the path
        // (/openai/deployments/...) rather than the host.
        // URL_TOKEN_OPENAI_COMPAT_PATH ("/v1/chat/completions") catches Ollama, vLLM, LM Studio,
        // and other OpenAI-compatible servers whose hostnames contain neither "openai" nor "deepseek".
        // supports_structured_output must be explicitly set to true on the connector action
        // (requires admin access), so an unintended match only results in an extra response_format
        // field being sent. Note: if the upstream provider rejects the extra field with a 4xx,
        // that surfaces as a failed predict call, not a silent fallback.
        if (hostHasSegment(host, URL_TOKEN_OPENAI) || pathHasSegment(path, URL_TOKEN_OPENAI)
            || hostHasSegment(host, URL_TOKEN_DEEPSEEK)
            || path.contains(URL_TOKEN_OPENAI_COMPAT_PATH)) {
            return Map.of("_response_format_json", FACTS_EXTRACTION_OPENAI_RESPONSE_FORMAT_JSON);
        }
        return Map.of();
    }

    private static boolean hostHasSegment(String host, String segment) {
        for (String label : host.split("\\.", -1)) {
            if (label.equals(segment)) return true;
        }
        return false;
    }

    private static boolean pathHasSegment(String path, String segment) {
        for (String seg : path.split("/", -1)) {
            if (seg.equals(segment)) return true;
        }
        return false;
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
