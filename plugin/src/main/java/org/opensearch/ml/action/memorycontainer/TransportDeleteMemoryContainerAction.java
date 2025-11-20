/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteAction;
import org.opensearch.ml.common.transport.memorycontainer.MLMemoryContainerDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.helper.MemoryContainerHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TransportDeleteMemoryContainerAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;
    final ConnectorAccessControlHelper connectorAccessControlHelper;
    final MemoryContainerHelper memoryContainerHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public TransportDeleteMemoryContainerAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MemoryContainerHelper memoryContainerHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLMemoryContainerDeleteAction.NAME, transportService, actionFilters, MLMemoryContainerDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.memoryContainerHelper = memoryContainerHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        if (!mlFeatureEnabledSetting.isAgenticMemoryEnabled()) {
            actionListener.onFailure(new OpenSearchStatusException(ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE, RestStatus.FORBIDDEN));
            return;
        }

        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest.fromActionRequest(request);
        String memoryContainerId = deleteRequest.getMemoryContainerId();
        String tenantId = deleteRequest.getTenantId();
        boolean deleteAllMemories = deleteRequest.isDeleteAllMemories();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        User user = RestActionUtils.getUserContext(client);

        // Get memory container and validate access
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate owner-only access (not backend roles)
            String ownerId = container.getOwner() != null ? container.getOwner().getName() : null;
            if (!memoryContainerHelper.checkMemoryAccess(user, ownerId)) {
                log.error("User doesn't have permissions to delete the memory container: {}", memoryContainerId);
                actionListener.onFailure(new OpenSearchStatusException("Only container owner can delete container", RestStatus.FORBIDDEN));
                return;
            }

            // CRITICAL: Check for shared index prefix BEFORE deleting container
            boolean willDeleteIndices = deleteRequest.isDeleteAllMemories() || !CollectionUtils.isEmpty(deleteRequest.getDeleteMemories());

            if (willDeleteIndices) {
                MemoryConfiguration configuration = container.getConfiguration();
                String indexPrefix = configuration.getIndexPrefix();

                memoryContainerHelper.countContainersWithPrefix(indexPrefix, ActionListener.wrap(count -> {
                    if (count > 1) {
                        // Multiple containers share this prefix - cannot delete indices
                        String error = String
                            .format(
                                "Cannot delete memory indices as multiple containers share the index prefix '%s'. "
                                    + "Please delete the container without index deletion, or use delete_by_query API for data cleanup.",
                                indexPrefix
                            );
                        log.error("Prevented index deletion for shared prefix '{}': {} containers sharing", indexPrefix, count);
                        actionListener.onFailure(new OpenSearchStatusException(error, RestStatus.CONFLICT));
                    } else {
                        // Safe to proceed with deletion (sole owner or unique prefix)
                        deleteMemoryContainer(
                            memoryContainerId,
                            container,
                            tenantId,
                            deleteRequest.isDeleteAllMemories(),
                            deleteRequest.getDeleteMemories(),
                            user,
                            actionListener
                        );
                    }
                }, e -> {
                    log.error("Failed to check for shared index prefix, aborting deletion for safety", e);
                    actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
                }));
            } else {
                // No index deletion requested, proceed with container-only deletion
                deleteMemoryContainer(
                    memoryContainerId,
                    container,
                    tenantId,
                    deleteRequest.isDeleteAllMemories(),
                    deleteRequest.getDeleteMemories(),
                    user,
                    actionListener
                );
            }
        }, error -> {
            log.error("Failed to retrieve memory container: {} for deletion", memoryContainerId, error);
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }));
    }

    private void deleteMemoryContainer(
        String memoryContainerId,
        MLMemoryContainer container,
        String tenantId,
        boolean deleteAllMemories,
        Set<MemoryType> deleteMemories,
        User user,
        ActionListener<DeleteResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest
                .builder()
                .index(ML_MEMORY_CONTAINER_INDEX)
                .id(memoryContainerId)
                .tenantId(tenantId)
                .build();
            sdkClient
                .deleteDataObjectAsync(deleteRequest)
                .whenComplete(
                    (deleteResponse, throwable) -> handleDeleteResponse(
                        deleteResponse,
                        throwable,
                        deleteRequest.id(),
                        deleteAllMemories,
                        deleteMemories,
                        container,
                        user,
                        listener
                    )
                );
        } catch (Exception e) {
            log.error("Failed to delete Memory Container: {}", memoryContainerId, e);
            listener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String memoryContainerId,
        boolean deleteAllMemories,
        Set<MemoryType> deleteMemories,
        MLMemoryContainer container,
        User user,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Memory Container {}", memoryContainerId, cause);
            actionListener.onFailure((new OpenSearchStatusException("Failed to find memory container", RestStatus.NOT_FOUND)));
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log
                    .info(
                        "Delete memory container - Event: CONTAINER_DELETED, Container ID: {}, User: {}, Timestamp: {}",
                        memoryContainerId,
                        user != null ? user.getName() : "unknown",
                        Instant.now()
                    );

                // Delete memory indices if requested
                // Note: Shared prefix validation already done BEFORE container deletion
                if (deleteAllMemories || (deleteMemories != null && !deleteMemories.isEmpty())) {
                    MemoryConfiguration configuration = container.getConfiguration();
                    if (deleteAllMemories) {
                        deleteAllMemoryIndices(memoryContainerId, configuration, user, deleteResponse, actionListener);
                    } else {
                        deleteSelectiveMemoryIndices(
                            memoryContainerId,
                            configuration,
                            deleteMemories,
                            user,
                            deleteResponse,
                            actionListener
                        );
                    }
                } else {
                    // No index deletion requested
                    actionListener.onResponse(deleteResponse);
                }
            } catch (Exception e) {
                log.error("Failed to process container deletion", e);
                actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
            }
        }
    }

    private void deleteAllMemoryIndices(
        String memoryContainerId,
        MemoryConfiguration configuration,
        User user,
        DeleteResponse deleteResponse,
        ActionListener<DeleteResponse> actionListener
    ) {
        // Don't use index pattern here to avoid delete other user's index by mistake
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(
            configuration.getSessionIndexName(),
            configuration.getWorkingMemoryIndexName(),
            configuration.getLongMemoryIndexName(),
            configuration.getLongMemoryHistoryIndexName()
        );

        log
            .debug(
                "Attempting to delete all memory indices for container {}: [{}, {}, {}, {}]",
                memoryContainerId,
                configuration.getSessionIndexName(),
                configuration.getWorkingMemoryIndexName(),
                configuration.getLongMemoryIndexName(),
                configuration.getLongMemoryHistoryIndexName()
            );
        memoryContainerHelper.deleteIndex(configuration, deleteIndexRequest, ActionListener.wrap(r -> {
            log
                .info(
                    "Delete memory container - Event: ALL_INDICES_DELETED, Container ID: {}, Indices: [{}, {}, {}, {}], User: {}, Timestamp: {}",
                    memoryContainerId,
                    configuration.getSessionIndexName(),
                    configuration.getWorkingMemoryIndexName(),
                    configuration.getLongMemoryIndexName(),
                    configuration.getLongMemoryHistoryIndexName(),
                    user != null ? user.getName() : "unknown",
                    Instant.now()
                );
            actionListener.onResponse(deleteResponse);
        }, e -> {
            log
                .error(
                    "Failed to delete memory indices for container: {}. Indices: [{}, {}, {}, {}]",
                    memoryContainerId,
                    configuration.getSessionIndexName(),
                    configuration.getWorkingMemoryIndexName(),
                    configuration.getLongMemoryIndexName(),
                    configuration.getLongMemoryHistoryIndexName(),
                    e
                );
            actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
        }));
    }

    private void deleteSelectiveMemoryIndices(
        String memoryContainerId,
        MemoryConfiguration configuration,
        Set<MemoryType> deleteMemories,
        User user,
        DeleteResponse deleteResponse,
        ActionListener<DeleteResponse> actionListener
    ) {
        List<String> indicesToDelete = new ArrayList<>();

        for (MemoryType memoryType : deleteMemories) {
            String indexName = null;
            switch (memoryType) {
                case SESSIONS:
                    indexName = configuration.getSessionIndexName();
                    break;
                case WORKING:
                    indexName = configuration.getWorkingMemoryIndexName();
                    break;
                case LONG_TERM:
                    indexName = configuration.getLongMemoryIndexName();
                    break;
                case HISTORY:
                    indexName = configuration.getLongMemoryHistoryIndexName();
                    break;
            }
            if (indexName != null) {
                indicesToDelete.add(indexName);
            }
        }

        if (!indicesToDelete.isEmpty()) {
            log
                .debug(
                    "Attempting selective deletion of memory indices for container {}: {}, requested types: {}",
                    memoryContainerId,
                    indicesToDelete,
                    deleteMemories
                );

            DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indicesToDelete.toArray(new String[0]));
            memoryContainerHelper.deleteIndex(configuration, deleteIndexRequest, ActionListener.wrap(r -> {
                log
                    .info(
                        "Delete memory container - Event: SELECTIVE_INDICES_DELETED, Container ID: {}, Indices: {}, Memory Types: {}, User: {}, Timestamp: {}",
                        memoryContainerId,
                        indicesToDelete,
                        deleteMemories,
                        user != null ? user.getName() : "unknown",
                        Instant.now()
                    );
                actionListener.onResponse(deleteResponse);
            }, e -> {
                log.error("Failed to delete selective memory indices [{}] for container: {}.", memoryContainerId, indicesToDelete, e);
                actionListener.onFailure(new OpenSearchStatusException("Internal server error", RestStatus.INTERNAL_SERVER_ERROR));
            }));
        } else {
            log.info("No valid memory indices to delete for container: {}", memoryContainerId);
            actionListener.onResponse(deleteResponse);
        }
    }
}
