/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_HISTORY;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_LONG_TERM;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_SESSIONS;
import static org.opensearch.ml.common.memorycontainer.MemoryContainerConstants.MEM_CONTAINER_MEMORY_TYPE_WORKING;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_AGENTIC_MEMORY_DISABLED_MESSAGE;

import java.util.ArrayList;
import java.util.List;

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
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.memorycontainer.MLMemoryContainer;
import org.opensearch.ml.common.memorycontainer.MemoryConfiguration;
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

            // Delete memory container
            deleteMemoryContainer(
                memoryContainerId,
                container,
                tenantId,
                deleteRequest.isDeleteAllMemories(),
                deleteRequest.getDeleteMemories(),
                actionListener
            );
        }, error -> {
            log.error("Failed to retrieve memory container: {} for deletion", memoryContainerId, error);
            actionListener.onFailure(error);
        }));
    }

    private void deleteMemoryContainer(
        String memoryContainerId,
        MLMemoryContainer container,
        String tenantId,
        boolean deleteAllMemories,
        List<String> deleteMemories,
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
                        listener
                    )
                );
        } catch (Exception e) {
            log.error("Failed to delete Memory Container: {}", memoryContainerId, e);
            listener.onFailure(e);
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String memoryContainerId,
        boolean deleteAllMemories,
        List<String> deleteMemories,
        MLMemoryContainer container,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Memory Container {}", memoryContainerId, cause);
            actionListener.onFailure((new OpenSearchStatusException("Failed to find memory container", RestStatus.NOT_FOUND)));
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log.info("Successfully deleted memory container: {} from index", memoryContainerId);

                if (deleteAllMemories) {
                    MemoryConfiguration configuration = container.getConfiguration();
                    // Don't use index pattern here to avoid delete other user's index by mistake
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(
                        configuration.getSessionIndexName(),
                        configuration.getWorkingMemoryIndexName(),
                        configuration.getLongMemoryIndexName(),
                        configuration.getLongMemoryHistoryIndexName()
                    );

                    log.info("Attempting to delete all memory indices for container {}: [{}, {}, {}, {}]",
                        memoryContainerId,
                        configuration.getSessionIndexName(),
                        configuration.getWorkingMemoryIndexName(),
                        configuration.getLongMemoryIndexName(),
                        configuration.getLongMemoryHistoryIndexName());
                    memoryContainerHelper
                        .deleteIndex(
                            configuration,
                            deleteIndexRequest,
                            ActionListener.wrap(r -> {
                                log.info("Successfully deleted all memory indices for container: {}", memoryContainerId);
                                actionListener.onResponse(deleteResponse);
                            }, e -> {
                                log.error("Failed to delete memory indices for container: {}. Indices: [{}, {}, {}, {}]",
                                    memoryContainerId,
                                    configuration.getSessionIndexName(),
                                    configuration.getWorkingMemoryIndexName(),
                                    configuration.getLongMemoryIndexName(),
                                    configuration.getLongMemoryHistoryIndexName(), e);
                                actionListener.onFailure(e);
                            })
                        );
                } else if (deleteMemories != null && !deleteMemories.isEmpty()) {
                    // Selective memory deletion
                    MemoryConfiguration configuration = container.getConfiguration();
                    List<String> indicesToDelete = new ArrayList<>();

                    for (String memoryType : deleteMemories) {
                        switch (memoryType) {
                            case MEM_CONTAINER_MEMORY_TYPE_SESSIONS:
                                indicesToDelete.add(configuration.getSessionIndexName());
                                break;
                            case MEM_CONTAINER_MEMORY_TYPE_WORKING:
                                indicesToDelete.add(configuration.getWorkingMemoryIndexName());
                                break;
                            case MEM_CONTAINER_MEMORY_TYPE_LONG_TERM:
                                indicesToDelete.add(configuration.getLongMemoryIndexName());
                                break;
                            case MEM_CONTAINER_MEMORY_TYPE_HISTORY:
                                indicesToDelete.add(configuration.getLongMemoryHistoryIndexName());
                                break;
                            default:
                                log.warn("Unknown memory type for deletion: {}", memoryType);
                        }
                    }

                    if (!indicesToDelete.isEmpty()) {
                        log.debug("Attempting selective deletion of memory indices for container {}: {}, requested types: {}",
                            memoryContainerId, indicesToDelete, deleteMemories);

                        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indicesToDelete.toArray(new String[0]));
                        memoryContainerHelper.deleteIndex(configuration, deleteIndexRequest, ActionListener.wrap(r -> {
                            log.info("Successfully deleted selective memory indices [{}] for container: {}",
                                indicesToDelete, memoryContainerId);
                            actionListener.onResponse(deleteResponse);
                        }, e -> {
                            log.error("Failed to delete selective memory indices [{}] for container: {}.",
                                memoryContainerId, indicesToDelete, e);
                            actionListener.onFailure(e);
                        }));
                    } else {
                        log.warn ("Memory container {} deleted without memory index deletion due to all provided types were unrecognized", memoryContainerId);
                        actionListener.onResponse(deleteResponse);
                    }
                } else { // No memory deletion requested
                    log.info("Memory container {} deleted without memory index deletion", memoryContainerId);
                    actionListener.onResponse(deleteResponse);
                }
            } catch (Exception e) {
                log.error("Unexpected error while processing delete response for container: {}",
                    memoryContainerId, e);
                actionListener.onFailure(e);
            }
        }
    }
}
