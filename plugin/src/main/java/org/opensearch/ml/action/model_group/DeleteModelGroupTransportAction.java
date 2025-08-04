/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_GROUP_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_MODEL_GROUP_ID;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.TermQueryBuilder;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteAction;
import org.opensearch.ml.common.transport.model_group.MLModelGroupDeleteRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteModelGroupTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    final Client client;
    final SdkClient sdkClient;
    final NamedXContentRegistry xContentRegistry;
    final ClusterService clusterService;

    final ModelAccessControlHelper modelAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService,
        ModelAccessControlHelper modelAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLModelGroupDeleteAction.NAME, transportService, actionFilters, MLModelGroupDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
        this.modelAccessControlHelper = modelAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLModelGroupDeleteRequest deleteRequest = MLModelGroupDeleteRequest.fromActionRequest(request);
        String modelGroupId = deleteRequest.getModelGroupId();
        String tenantId = deleteRequest.getTenantId();

        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, context::restore);
            validateAndDeleteModelGroup(modelGroupId, tenantId, wrappedListener);
        }
    }

    private void validateAndDeleteModelGroup(String modelGroupId, String tenantId, ActionListener<DeleteResponse> listener) {
        User user = RestActionUtils.getUserContext(client);
        modelAccessControlHelper
            .validateModelGroupAccess(
                user,
                mlFeatureEnabledSetting,
                tenantId,
                modelGroupId,
                client,
                sdkClient,
                ActionListener
                    .wrap(
                        hasAccess -> handleAccessValidation(hasAccess, modelGroupId, tenantId, listener),
                        error -> handleValidationError(error, modelGroupId, listener)
                    )
            );
    }

    private void handleAccessValidation(boolean hasAccess, String modelGroupId, String tenantId, ActionListener<DeleteResponse> listener) {
        if (!hasAccess) {
            listener.onFailure(new MLValidationException("User doesn't have privilege to delete this model group"));
            return;
        }
        checkForAssociatedModels(modelGroupId, tenantId, listener);
    }

    private void checkForAssociatedModels(String modelGroupId, String tenantId, ActionListener<DeleteResponse> listener) {
        SearchDataObjectRequest searchRequest = buildModelSearchRequest(modelGroupId, tenantId);

        sdkClient
            .searchDataObjectAsync(searchRequest)
            .whenComplete(
                (searchResponse, throwable) -> handleModelSearchResponse(searchResponse, throwable, modelGroupId, tenantId, listener)
            );
    }

    private SearchDataObjectRequest buildModelSearchRequest(String modelGroupId, String tenantId) {
        BoolQueryBuilder query = new BoolQueryBuilder().filter(new TermQueryBuilder(PARAMETER_MODEL_GROUP_ID, modelGroupId));
        SearchSourceBuilder searchSource = new SearchSourceBuilder().query(query);

        return SearchDataObjectRequest.builder().indices(ML_MODEL_INDEX).tenantId(tenantId).searchSourceBuilder(searchSource).build();
    }

    private void handleModelSearchResponse(
        SearchDataObjectResponse searchResponse,
        Throwable throwable,
        String modelGroupId,
        String tenantId,
        ActionListener<DeleteResponse> listener
    ) {
        if (searchResponse == null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleModelSearchFailure(modelGroupId, tenantId, cause, listener);
            return;
        }

        try {
            SearchResponse response = searchResponse.searchResponse();
            // Parsing failure would cause NPE on next line
            if (response.getHits().getHits().length == 0) {
                DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
                deleteModelGroup(deleteRequest, tenantId, listener);
            } else {
                listener
                    .onFailure(
                        new OpenSearchStatusException(
                            "Cannot delete the model group when it has associated model versions",
                            RestStatus.CONFLICT
                        )
                    );
            }
        } catch (Exception e) {
            log.error("Failed to parse search response", e);
            listener.onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void deleteModelGroup(DeleteRequest deleteRequest, String tenantId, ActionListener<DeleteResponse> listener) {
        try {
            DeleteDataObjectRequest request = DeleteDataObjectRequest
                .builder()
                .index(deleteRequest.index())
                .id(deleteRequest.id())
                .tenantId(tenantId)
                .build();

            sdkClient
                .deleteDataObjectAsync(request)
                .whenComplete((response, throwable) -> handleDeleteResponse(response, throwable, deleteRequest.id(), listener));
        } catch (Exception e) {
            log.error("Failed to delete Model group : {}", deleteRequest.id(), e);
            listener.onFailure(e);
        }
    }

    private void handleValidationError(Exception error, String modelGroupId, ActionListener<DeleteResponse> listener) {
        log.error("Failed to validate Access for Model Group {}", modelGroupId, error);
        listener.onFailure(error);
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String modelGroupId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML Model Group {}", modelGroupId, cause);
            if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                actionListener.onFailure(new OpenSearchStatusException("Failed to find model group", RestStatus.NOT_FOUND));
                return;
            }
            actionListener.onFailure(cause);
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log.debug("Completed Delete Model Group Request, model group id:{} deleted", response.id());
                actionListener.onResponse(deleteResponse);
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }
    }

    private void handleModelSearchFailure(
        String modelGroupId,
        String tenantId,
        Exception cause,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
            DeleteRequest deleteRequest = new DeleteRequest(ML_MODEL_GROUP_INDEX, modelGroupId);
            deleteModelGroup(deleteRequest, tenantId, actionListener);
            return;
        }

        log.error("Failed to search for models using model group id: {}", modelGroupId, cause);
        actionListener.onFailure(cause);
    }
}
