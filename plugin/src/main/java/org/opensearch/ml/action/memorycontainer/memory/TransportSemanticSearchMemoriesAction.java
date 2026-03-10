/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import org.opensearch.OpenSearchException;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSemanticSearchMemoriesRequest;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.MemorySearchQueryBuilder;
import org.opensearch.ml.utils.MemorySearchValidationUtils;
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
        MLSemanticSearchMemoriesInput input = request.getMlSemanticSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (!MemorySearchValidationUtils.validateSearchRequest(mlFeatureEnabledSetting, input, tenantId, "Semantic", actionListener)) {
            return;
        }
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        memoryContainerHelper.getMemoryContainer(input.getMemoryContainerId(), tenantId, ActionListener.wrap(container -> {
            User user = RestActionUtils.getUserContext(client);
            if (!MemorySearchValidationUtils.validateContainerConfig(container, user, memoryContainerHelper, "Semantic", actionListener)) {
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
            String ownerId = MemorySearchValidationUtils.resolveOwnerId(user, memoryContainerHelper);

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
