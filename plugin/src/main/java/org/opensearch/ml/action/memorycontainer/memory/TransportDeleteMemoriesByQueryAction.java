/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.OWNER_ID_FIELD;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.util.Locale;

import org.opensearch.OpenSearchStatusException;
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
import org.opensearch.index.reindex.BulkByScrollResponse;
import org.opensearch.index.reindex.DeleteByQueryAction;
import org.opensearch.index.reindex.DeleteByQueryRequest;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryAction;
import org.opensearch.ml.common.transport.memorycontainer.memory.MLDeleteMemoriesByQueryRequest;
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
public class TransportDeleteMemoriesByQueryAction extends HandledTransportAction<MLDeleteMemoriesByQueryRequest, BulkByScrollResponse> {

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
    protected void doExecute(Task task, MLDeleteMemoriesByQueryRequest request, ActionListener<BulkByScrollResponse> actionListener) {
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
            String memoryType = request.getMemoryType();
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

            // Step 5: Build the DeleteByQueryRequest
            DeleteByQueryRequest deleteByQueryRequest = new DeleteByQueryRequest(memoryIndexName);

            // Step 6: Apply query with owner filtering for non-admin users
            QueryBuilder finalQuery = buildFilteredQuery(request.getQuery(), user);
            deleteByQueryRequest.setQuery(finalQuery);
            deleteByQueryRequest.setRefresh(true);

            // Step 7: Execute the delete by query
            executeDeleteByQuery(container.getConfiguration(), deleteByQueryRequest, actionListener);

        }, error -> {
            log.error("Failed to get memory container: " + memoryContainerId, error);
            actionListener.onFailure(error);
        }));
    }

    /**
     * Get the memory index name based on the memory type
     */
    private String getMemoryIndexName(MLMemoryContainer container, String memoryType) {
        if (container == null || container.getConfiguration() == null) {
            return null;
        }

        MemoryConfiguration config = container.getConfiguration();
        String normalizedType = memoryType.toLowerCase(Locale.ROOT);

        switch (normalizedType) {
            case "session":
                return config.isDisableSession() ? null : config.getSessionIndexName();
            case "working":
                return config.getWorkingMemoryIndexName();
            case "long_term":
                return config.getLongMemoryIndexName();
            case "history":
                return config.isDisableHistory() ? null : config.getLongMemoryHistoryIndexName();
            default:
                return null;
        }
    }

    /**
     * Build a filtered query that includes owner filtering for non-admin users
     */
    private QueryBuilder buildFilteredQuery(QueryBuilder userQuery, User user) {
        // If security is disabled or user is admin, use the original query
        if (user == null || (user.getRoles() != null && user.getRoles().contains("all_access"))) {
            return userQuery;
        }

        // For non-admin users, add owner filter
        BoolQueryBuilder filteredQuery = QueryBuilders.boolQuery();
        filteredQuery.must(userQuery);
        filteredQuery.filter(QueryBuilders.termQuery(OWNER_ID_FIELD, user.getName()));

        return filteredQuery;
    }

    /**
     * Execute the delete by query request, handling system indices appropriately
     */
    private void executeDeleteByQuery(
        MemoryConfiguration configuration,
        DeleteByQueryRequest deleteByQueryRequest,
        ActionListener<BulkByScrollResponse> actionListener
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

            log.info("Delete by query completed. Deleted {} documents in {} ms", response.getDeleted(), response.getTook().millis());
            actionListener.onResponse(response);
        }, error -> {
            log.error("Failed to execute delete by query", error);
            actionListener.onFailure(error);
        });

        // Execute with appropriate context based on system index setting
        if (configuration.isUseSystemIndex()) {
            try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                client
                    .execute(
                        DeleteByQueryAction.INSTANCE,
                        deleteByQueryRequest,
                        ActionListener.runBefore(wrappedListener, context::restore)
                    );
            } catch (Exception e) {
                log.error("Failed to execute delete by query on system index", e);
                actionListener.onFailure(e);
            }
        } else {
            client.execute(DeleteByQueryAction.INSTANCE, deleteByQueryRequest, wrappedListener);
        }
    }
}
