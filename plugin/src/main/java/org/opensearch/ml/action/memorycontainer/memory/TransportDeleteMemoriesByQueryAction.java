/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryResponse;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action for deleting memories by query
 */
@Log4j2
public class TransportDeleteMemoriesByQueryAction extends
    HandledTransportAction<MLDeleteMemoriesByQueryRequest, MLDeleteMemoriesByQueryResponse> {

    private final Client client;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    private final MemoryContainerHelper memoryContainerHelper;

    @Inject
    public TransportDeleteMemoriesByQueryAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        MemoryContainerHelper memoryContainerHelper
    ) {
        super(MLDeleteMemoriesByQueryAction.NAME, transportService, actionFilters, MLDeleteMemoriesByQueryRequest::new);
        this.client = client;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        this.memoryContainerHelper = memoryContainerHelper;
    }

    @Override
    protected void doExecute(
        Task task,
        MLDeleteMemoriesByQueryRequest request,
        ActionListener<MLDeleteMemoriesByQueryResponse> actionListener
    ) {
        // Step 1: Check if agentic memory feature is enabled
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        // Step 2: Get user context
        User user = RestActionUtils.getUserContext(client);

        // Step 3: Get memory container and check access
        String memoryContainerId = request.getMemoryContainerId();
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Check container access
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "User doesn't have permissions to delete memories in this container",
                            RestStatus.FORBIDDEN
                        )
                    );
                return;
            }

            // Step 4: Get the memory index name based on type
            MemoryType memoryType = request.getMemoryType();
            String memoryIndexName = getMemoryIndexName(container, memoryType);

            if (memoryIndexName == null) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Invalid memory type or memory type is disabled: " + memoryType,
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            // Step 5: Validate query is provided
            QueryBuilder query = request.getQuery();
            if (query == null) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Query parameter is required for delete by query operation. "
                                + "To delete all documents, explicitly provide a match_all query: {\"query\": {\"match_all\": {}}}",
                            RestStatus.BAD_REQUEST
                        )
                    );
                return;
            }

            // Step 6: Build the DeleteByQueryRequest
            DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(memoryIndexName);

            // Step 7: Apply container ID filter to prevent cross-container deletion when containers share index prefix
            QueryBuilder containerFilteredQuery = memoryContainerHelper.addContainerIdFilter(memoryContainerId, query);

            // Step 8: Apply owner filtering for non-admin users
            QueryBuilder finalQuery = memoryContainerHelper.addOwnerIdFilter(user, containerFilteredQuery);
            deleteByQueryRequest.setQuery(finalQuery);
            deleteByQueryRequest.setRefresh(true);

            // Step 9: Execute the delete by query
            executeDeleteByQuery(memoryContainerId, memoryType, user, container.getConfiguration(), deleteByQueryRequest, actionListener);

        }, error -> {
            log.error("Failed to get memory container: " + memoryContainerId, error);
            actionListener.onFailure(error);
        }));
    }

    /**
     * Get the memory index name based on the memory type
     */
    private String getMemoryIndexName(MLMemoryContainer container, MemoryType memoryType) {
        if (container == null || container.getConfiguration() == null) {
            return null;
        }

        MemoryConfiguration config = container.getConfiguration();

        switch (memoryType) {
            case SESSIONS:
                return config.isDisableSession() ? null : config.getSessionIndexName();
            case WORKING:
                return config.getWorkingMemoryIndexName();
            case LONG_TERM:
                return config.getLongMemoryIndexName();
            case HISTORY:
                return config.isDisableHistory() ? null : config.getLongMemoryHistoryIndexName();
            default:
                return null;
        }
    }

    /**
     * Execute the delete by query request, handling system indices appropriately
     */
    private void executeDeleteByQuery(
        String memoryContainerId,
        MemoryType memoryType,
        User user,
        MemoryConfiguration configuration,
        DeleteByQueryRequest deleteByQueryRequest,
        ActionListener<MLDeleteMemoriesByQueryResponse> actionListener
    ) {
        // Wrap the listener to handle response and log results
        ActionListener<BulkByScrollResponse> wrappedListener = ActionListener.wrap(response -> {
            // Log any failures
            if (response.getBulkFailures() != null && !response.getBulkFailures().isEmpty()) {
                log.warn("Bulk failures during delete by query: {}", response.getBulkFailures());
            }
            if (response.getSearchFailures() != null && !response.getSearchFailures().isEmpty()) {
                log.warn("Search failures during delete by query: {}", response.getSearchFailures());
            }
            if (response.isTimedOut()) {
                log.warn("Delete by query operation timed out");
            }

            log
                .info(
                    "Delete memories by query - Event: MEMORIES_DELETED_BY_QUERY, Container ID: {}, Memory Type: {}, Deleted Count: {}, Duration: {}ms, User: {}, Timestamp: {}",
                    memoryContainerId,
                    memoryType,
                    response.getDeleted(),
                    response.getTook().millis(),
                    user != null ? user.getName() : "unknown",
                    Instant.now()
                );
            // Wrap the BulkByScrollResponse in our response wrapper
            actionListener.onResponse(new MLDeleteMemoriesByQueryResponse(response));
        }, error -> {
            log.error("Failed to execute delete by query", error);
            actionListener.onFailure(error);
        });

        // Use the helper method to execute with appropriate context
        memoryContainerHelper.deleteDataByQuery(configuration, deleteByQueryRequest, wrappedListener);
    }
}
