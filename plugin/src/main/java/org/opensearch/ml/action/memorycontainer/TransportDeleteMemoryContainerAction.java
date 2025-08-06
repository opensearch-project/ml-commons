/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer;

import static org.opensearch.ml.common.CommonValue.ML_MEMORY_CONTAINER_INDEX;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
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
        MLMemoryContainerDeleteRequest deleteRequest = MLMemoryContainerDeleteRequest.fromActionRequest(request);
        String memoryContainerId = deleteRequest.getMemoryContainerId();
        String tenantId = deleteRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        User user = RestActionUtils.getUserContext(client);

        // Get memory container and validate access
        memoryContainerHelper.getMemoryContainer(memoryContainerId, ActionListener.wrap(container -> {
            // Validate access permissions
            if (!memoryContainerHelper.checkMemoryContainerAccess(user, container)) {
                actionListener
                    .onFailure(
                        new OpenSearchStatusException("User doesn't have permissions to delete this memory container", RestStatus.FORBIDDEN)
                    );
                return;
            }

            // Delete memory container
            deleteMemoryContainer(memoryContainerId, tenantId, actionListener);
        }, actionListener::onFailure));
    }

    private void deleteMemoryContainer(String memoryContainerId, String tenantId, ActionListener<DeleteResponse> listener) {
        try {
            DeleteDataObjectRequest deleteRequest = DeleteDataObjectRequest
                .builder()
                .index(ML_MEMORY_CONTAINER_INDEX)
                .id(memoryContainerId)
                .tenantId(tenantId)
                .build();
            sdkClient
                .deleteDataObjectAsync(deleteRequest)
                .whenComplete((deleteResponse, throwable) -> handleDeleteResponse(deleteResponse, throwable, deleteRequest.id(), listener));
        } catch (Exception e) {
            log.error("Failed to delete Memory Container: {}", memoryContainerId, e);
            listener.onFailure(e);
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String memoryContainerId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Memory Container {}", memoryContainerId, cause);
            actionListener.onFailure((new OpenSearchStatusException("Failed to find memory container", RestStatus.NOT_FOUND)));
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log.debug("Completed Delete Memory Container Request, memory container id:{} deleted", response.id());
                actionListener.onResponse(deleteResponse);
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }
    }
}
