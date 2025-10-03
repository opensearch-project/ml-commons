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
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesInput;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLSearchMemoriesRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class TransportSearchMemoriesAction extends HandledTransportAction<MLSearchMemoriesRequest, SearchResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportSearchMemoriesAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLSearchMemoriesAction.NAME, transportService, actionFilters, MLSearchMemoriesRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(Task task, MLSearchMemoriesRequest request, ActionListener<SearchResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLSearchMemoriesInput input = request.getMlSearchMemoriesInput();
        String tenantId = request.getTenantId();

        if (input == null) {
            actionListener.onFailure(new IllegalArgumentException("Search memories input is required"));
            return;
        }

        if (StringUtils.isBlank(input.getMemoryContainerId())) {
            actionListener.onFailure(new IllegalArgumentException("Memory container ID is required"));
            return;
        }

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        // Get memory container first to validate access and get search configuration
        memoryContainerHelper.getMemoryContainer(input.getMemoryContainerId(), tenantId, ActionListener.wrap(container -> {
            // Validate access permissions
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

            // Execute search based on container configuration
            searchMemories(input, container, user, tenantId, actionListener);

        }, actionListener::onFailure));
    }

    private void searchMemories(
        MLSearchMemoriesInput input,
        MLMemoryContainer container,
        User user,
        String tenantId,
        ActionListener<SearchResponse> actionListener
    ) {
        try {
            MemoryConfiguration memoryConfig = container.getConfiguration();
            String indexName = memoryConfig.getIndexName(input.getMemoryType());

            if (!memoryContainerHelper.isAdminUser(user)) {
                memoryContainerHelper.addOwnerIdFilter(user, input.getSearchSourceBuilder());
            }

            SearchDataObjectRequest searchDataObjecRequest = SearchDataObjectRequest
                .builder()
                .indices(indexName)
                .searchSourceBuilder(input.getSearchSourceBuilder())
                .tenantId(tenantId)
                .build();

            // Execute search
            ActionListener<SearchResponse> searchResponseActionListener = ActionListener.wrap(response -> {
                try {
                    actionListener.onResponse(response);
                } catch (Exception e) {
                    log.error("Failed to parse search response", e);
                    actionListener.onFailure(new OpenSearchException("Failed to parse search response", e));
                }
            }, e -> {
                log.error("Search execution failed", e);
                actionListener.onFailure(new OpenSearchException("Search execution failed: " + e.getMessage(), e));
            });
            memoryContainerHelper.searchData(container.getConfiguration(), searchDataObjecRequest, searchResponseActionListener);

        } catch (Exception e) {
            log.error("Failed to build search request", e);
            actionListener.onFailure(new OpenSearchException("Failed to build search request: " + e.getMessage(), e));
        }
    }
}
