/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLHybridSearchMemoriesRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportHybridSearchMemoriesAction extends HandledTransportAction<MLHybridSearchMemoriesRequest, SearchResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportHybridSearchMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLHybridSearchMemoriesAction.NAME, transportService, actionFilters, MLHybridSearchMemoriesRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLHybridSearchMemoriesRequest request, ActionListener<SearchResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLHybridSearchMemoriesInput input = request.getMlHybridSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Hybrid search input is required"));
            return;
        }
        if (StringUtils.isBlank(input.getMemoryContainerId())) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        memoryContainerHelper.getMemoryContainer(input.getMemoryContainerId(), tenantId, ActionListener.wrap(container -> {
            User user = RestActionUtils.getUserContext(client);
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to search memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            MemoryConfiguration memoryConfig = container.getConfiguration();

            if (memoryConfig == null || memoryConfig.getEmbeddingModelType() == null) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "This memory container does not have an embedding model configured. "
                                + "Hybrid search requires a memory container with an embedding model (TEXT_EMBEDDING or SPARSE_ENCODING). "
                                + "Please update the memory container configuration to add an embedding model.",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            if (StringUtils.isBlank(memoryConfig.getEmbeddingModelId())) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "This memory container does not have an embedding model ID configured. "
                                + "Please update the memory container configuration to add an embedding model ID.",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            if (memoryConfig.getStrategies() == null || memoryConfig.getStrategies().isEmpty()) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "This memory container does not have any memory strategies configured. "
                                + "Hybrid search requires long-term memories which are created through memory strategies. "
                                + "Please update the memory container configuration to add at least one strategy.",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            executeHybridSearch(input, container, user, actionListener);
        }, actionListener::onFailure));
    }

    private void executeHybridSearch(
        MLHybridSearchMemoriesInput input,
        MLMemoryContainer container,
        User user,
        ActionListener<SearchResponse> actionListener
    ) {
        try {
            MemoryConfiguration memoryConfig = container.getConfiguration();
            String ownerId = (user != null && !memoryContainerHelper.isAdminUser(user)) ? user.getName() : null;

            // Build hybrid query (match + neural) via wrapperQuery
            String hybridQueryString = MemorySearchQueryBuilder.buildHybridSearchQueryString(input.getQuery(), memoryConfig);

            // Build post_filter for namespace/tag/owner/container filtering
            // (hybrid query cannot be wrapped in bool, so filters go in post_filter)
            QueryBuilder postFilter = MemorySearchQueryBuilder
                .buildPostFilter(input.getNamespace(), input.getTags(), ownerId, input.getMemoryContainerId(), input.getFilter());

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(QueryBuilders.wrapperQuery(hybridQueryString));
            // Set post_filter only when there are actual filter clauses
            if (postFilter instanceof BoolQueryBuilder boolPostFilter && !boolPostFilter.filter().isEmpty()) {
                searchSourceBuilder.postFilter(postFilter);
            } else if (!(postFilter instanceof BoolQueryBuilder)) {
                // Non-bool post filter (e.g. custom filter) — always apply
                searchSourceBuilder.postFilter(postFilter);
            }
            searchSourceBuilder.size(input.getK());
            searchSourceBuilder.fetchSource(null, new String[] { "memory_embedding" });
            if (input.getMinScore() != null) {
                searchSourceBuilder.minScore(input.getMinScore());
            }

            // Build SearchRequest with pipeline parameter — using client.search() directly
            // because SearchDataObjectRequest doesn't support the pipeline parameter
            String pipelineName = memoryConfig.getLongMemoryIndexName() + "-hybrid-search";
            SearchRequest searchRequest = new SearchRequest(memoryConfig.getLongMemoryIndexName());
            searchRequest.source(searchSourceBuilder);
            searchRequest.pipeline(pipelineName);

            // Handle system index context stashing if needed
            if (memoryConfig.isUseSystemIndex()) {
                // Stash context and restore it after the async response — do NOT use try-with-resources
                // because the context must remain open until the async callback fires
                ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext();
                ActionListener<SearchResponse> wrappedListener = ActionListener
                    .runBefore(ActionListener.wrap(actionListener::onResponse, e -> {
                        log.error("Hybrid search execution failed", e);
                        actionListener.onFailure(new OpenSearchException("Hybrid search execution failed: " + e.getMessage(), e));
                    }), context::restore);
                client.search(searchRequest, wrappedListener);
            } else {
                client.search(searchRequest, ActionListener.wrap(actionListener::onResponse, e -> {
                    log.error("Hybrid search execution failed", e);
                    actionListener.onFailure(new OpenSearchException("Hybrid search execution failed: " + e.getMessage(), e));
                }));
            }
        } catch (Exception e) {
            log.error("Failed to build hybrid search request", e);
            actionListener.onFailure(new OpenSearchException("Failed to build hybrid search request: " + e.getMessage(), e));
        }
    }
}
