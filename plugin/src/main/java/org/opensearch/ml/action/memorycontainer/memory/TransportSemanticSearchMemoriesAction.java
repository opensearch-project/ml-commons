/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import org.apache.commons.lang3.StringUtils;
import org.opensearch.OpenSearchException;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportSemanticSearchMemoriesAction extends HandledTransportAction<MLSemanticSearchMemoriesRequest, SearchResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportSemanticSearchMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLSemanticSearchMemoriesAction.NAME, transportService, actionFilters, MLSemanticSearchMemoriesRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLSemanticSearchMemoriesRequest request, ActionListener<SearchResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLSemanticSearchMemoriesInput input = request.getMlSemanticSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Semantic search input is required"));
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

            // Validate embedding model is configured
            if (memoryConfig == null || memoryConfig.getEmbeddingModelType() == null) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "This memory container does not have an embedding model configured. "
                                + "Semantic search requires a memory container with an embedding model (TEXT_EMBEDDING or SPARSE_ENCODING). "
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
                                + "Semantic search requires long-term memories which are created through memory strategies. "
                                + "Please update the memory container configuration to add at least one strategy.",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            executeSemanticSearch(input, container, user, tenantId, actionListener);
        }, actionListener::onFailure));
    }

    private void executeSemanticSearch(
        MLSemanticSearchMemoriesInput input,
        MLMemoryContainer container,
        User user,
        String tenantId,
        ActionListener<SearchResponse> actionListener
    ) {
        try {
            MemoryConfiguration memoryConfig = container.getConfiguration();
            String ownerId = (user != null && !ConnectorAccessControlHelper.isAdmin(user)) ? user.getName() : null;

            QueryBuilder queryBuilder = MemorySearchQueryBuilder
                .buildSemanticSearchQuery(
                    input.getQuery(),
                    input.getNamespace(),
                    input.getTags(),
                    ownerId,
                    input.getMemoryContainerId(),
                    memoryConfig,
                    input.getFilter()
                );

            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
            searchSourceBuilder.query(queryBuilder);
            searchSourceBuilder.size(input.getK());
            searchSourceBuilder.fetchSource(null, new String[] { "memory_embedding" });
            if (input.getMinScore() != null) {
                searchSourceBuilder.minScore(input.getMinScore());
            }

            SearchDataObjectRequest searchRequest = SearchDataObjectRequest
                .builder()
                .indices(memoryConfig.getLongMemoryIndexName())
                .searchSourceBuilder(searchSourceBuilder)
                .tenantId(tenantId)
                .build();

            memoryContainerHelper.searchData(memoryConfig, searchRequest, ActionListener.wrap(actionListener::onResponse, e -> {
                log.error("Semantic search execution failed", e);
                actionListener.onFailure(new OpenSearchException("Semantic search execution failed: " + e.getMessage(), e));
            }));
        } catch (Exception e) {
            log.error("Failed to build semantic search request", e);
            actionListener.onFailure(new OpenSearchException("Failed to build semantic search request: " + e.getMessage(), e));
        }
    }
}
