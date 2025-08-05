/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.*;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryStorageConfig;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesResponse;
import org.opensearch.ml.common.transport.memorycontainer.memory.MemorySearchResult;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.GetDataObjectRequest;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportSearchMemoriesAction extends HandledTransportAction<MLSearchMemoriesRequest, MLSearchMemoriesResponse> {

    private final Client client;
    private final SdkClient sdkClient;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final NamedXContentRegistry xContentRegistry;

    @Inject
    public TransportSearchMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLSearchMemoriesAction.NAME, transportService, actionFilters, MLSearchMemoriesRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, MLSearchMemoriesRequest request, ActionListener<MLSearchMemoriesResponse> actionListener) {
        MLSearchMemoriesInput input = request.getMlSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Search memories input is required"));
            return;
        }

        if (input.getMemoryContainerId() == null || input.getMemoryContainerId().trim().isEmpty()) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        // Get memory container first to validate access and get search configuration
        getMemoryContainer(input.getMemoryContainerId(), tenantId, ActionListener.wrap(container -> {
            // Validate access permissions
            User user = RestActionUtils.getUserContext(client);
            if (!checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to search memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Execute search based on container configuration
            searchMemories(input, container, actionListener);

        }, actionListener::onFailure));
    }

    private void getMemoryContainer(String memoryContainerId, String tenantId, ActionListener<MLMemoryContainer> listener) {
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_MEMORY_CONTAINER_INDEX)
            .id(memoryContainerId)
            .tenantId(tenantId)
            .fetchSourceContext(fetchSourceContext)
            .build();

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
        } catch (Exception e) {
            log.error("Failed to get memory container {}", memoryContainerId, e);
            listener.onFailure(e);
        }
    }

    private void searchMemories(
        MLSearchMemoriesInput input,
        MLMemoryContainer container,
        ActionListener<MLSearchMemoriesResponse> actionListener
    ) {
        try {
            MemoryStorageConfig storageConfig = container.getMemoryStorageConfig();
            String indexName = storageConfig != null
                ? storageConfig.getMemoryIndexName()
                : STATIC_MEMORY_INDEX_PREFIX + container.getName().toLowerCase() + "-" + RestActionUtils.getUserContext(client).getName();

            // Build search request based on storage configuration
            SearchRequest searchRequest = buildSearchRequest(input.getQuery(), storageConfig, indexName);

            // Execute search
            client.search(searchRequest, ActionListener.wrap(response -> {
                try {
                    MLSearchMemoriesResponse searchResponse = parseSearchResponse(response);
                    actionListener.onResponse(searchResponse);
                } catch (Exception e) {
                    log.error("Failed to parse search response", e);
                    actionListener.onFailure(new OpenSearchException("Failed to parse search response", e));
                }
            }, e -> {
                log.error("Search execution failed", e);
                actionListener.onFailure(new OpenSearchException("Search execution failed: " + e.getMessage(), e));
            }));

        } catch (Exception e) {
            log.error("Failed to build search request", e);
            actionListener.onFailure(new OpenSearchException("Failed to build search request: " + e.getMessage(), e));
        }
    }

    private SearchRequest buildSearchRequest(String query, MemoryStorageConfig storageConfig, String indexName) throws IOException {
        // Note: Size limit removed - search will return all matching results
        // int maxResults = storageConfig != null ? storageConfig.getMaxInferSize() : MAX_INFER_SIZE_DEFAULT_VALUE;

        // Use utility class to build the appropriate query
        XContentBuilder queryBuilder = MemorySearchQueryBuilder.buildQueryByStorageType(query, storageConfig);

        // Build search source with exclusions
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.wrapperQuery(queryBuilder.toString()));
        // Size limit removed - will return all matching results
        // searchSourceBuilder.size(maxResults);
        searchSourceBuilder.fetchSource(null, new String[] { MEMORY_EMBEDDING_FIELD });

        return new SearchRequest().indices(indexName).source(searchSourceBuilder);
    }

    private MLSearchMemoriesResponse parseSearchResponse(SearchResponse searchResponse) throws IOException {
        List<MemorySearchResult> results = new ArrayList<>();
        float maxScore = searchResponse.getHits().getMaxScore();

        for (SearchHit hit : searchResponse.getHits().getHits()) {
            Map<String, Object> sourceMap = hit.getSourceAsMap();

            // Parse memory fields from source
            String memoryId = hit.getId();
            String memory = (String) sourceMap.get(MEMORY_FIELD);
            float score = hit.getScore();
            String sessionId = (String) sourceMap.get(SESSION_ID_FIELD);
            String agentId = (String) sourceMap.get(AGENT_ID_FIELD);
            String userId = (String) sourceMap.get(USER_ID_FIELD);
            String role = (String) sourceMap.get(ROLE_FIELD);

            // Parse memory type
            MemoryType memoryType = null;
            String memoryTypeStr = (String) sourceMap.get(MEMORY_TYPE_FIELD);
            if (memoryTypeStr != null) {
                try {
                    memoryType = MemoryType.valueOf(memoryTypeStr);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid memory type: {}", memoryTypeStr);
                }
            }

            // Parse tags
            @SuppressWarnings("unchecked")
            Map<String, String> tags = (Map<String, String>) sourceMap.get(TAGS_FIELD);

            // Parse timestamps
            Instant createdTime = null;
            Instant lastUpdatedTime = null;
            try {
                Object createdTimeObj = sourceMap.get(CREATED_TIME_FIELD);
                if (createdTimeObj instanceof Number) {
                    createdTime = Instant.ofEpochMilli(((Number) createdTimeObj).longValue());
                }

                Object lastUpdatedTimeObj = sourceMap.get(LAST_UPDATED_TIME_FIELD);
                if (lastUpdatedTimeObj instanceof Number) {
                    lastUpdatedTime = Instant.ofEpochMilli(((Number) lastUpdatedTimeObj).longValue());
                }
            } catch (Exception e) {
                log.warn("Failed to parse timestamps", e);
            }

            MemorySearchResult result = MemorySearchResult
                .builder()
                .memoryId(memoryId)
                .memory(memory)
                .score(score)
                .sessionId(sessionId)
                .agentId(agentId)
                .userId(userId)
                .memoryType(memoryType)
                .role(role)
                .tags(tags)
                .createdTime(createdTime)
                .lastUpdatedTime(lastUpdatedTime)
                .build();

            results.add(result);
        }

        return MLSearchMemoriesResponse
            .builder()
            .hits(results)
            .totalHits(searchResponse.getHits().getTotalHits().value())
            .maxScore(maxScore)
            .timedOut(searchResponse.isTimedOut())
            .build();
    }

    private boolean checkMemoryContainerAccess(User user, MLMemoryContainer mlMemoryContainer) {
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

        // Check if user has matching backend roles
        if (owner != null && owner.getBackendRoles() != null && user.getBackendRoles() != null) {
            return owner.getBackendRoles().stream().anyMatch(role -> user.getBackendRoles().contains(role));
        }

        return false;
    }
}
