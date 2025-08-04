/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensearch.ExceptionsHelper;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.remote.metadata.client.DeleteDataObjectRequest;
import org.opensearch.remote.metadata.client.DeleteDataObjectResponse;
import org.opensearch.remote.metadata.client.SdkClient;
import org.opensearch.remote.metadata.client.SearchDataObjectRequest;
import org.opensearch.remote.metadata.client.SearchDataObjectResponse;
import org.opensearch.remote.metadata.common.SdkClientUtils;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteConnectorTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    private final Client client;
    private final SdkClient sdkClient;
    private final NamedXContentRegistry xContentRegistry;
    private final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    @Inject
    public DeleteConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLConnectorDeleteAction.NAME, transportService, actionFilters, MLConnectorDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.fromActionRequest(request);
        String connectorId = mlConnectorDeleteRequest.getConnectorId();
        String tenantId = mlConnectorDeleteRequest.getTenantId();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, tenantId, actionListener)) {
            return;
        }
        connectorAccessControlHelper
            .validateConnectorAccess(
                sdkClient,
                client,
                connectorId,
                tenantId,
                mlFeatureEnabledSetting,
                ActionListener
                    .wrap(
                        isAllowed -> handleConnectorAccessValidation(connectorId, tenantId, isAllowed, actionListener),
                        e -> handleConnectorAccessValidationFailure(connectorId, e, actionListener)
                    )
            );
    }

    private void handleConnectorAccessValidation(
        String connectorId,
        String tenantId,
        boolean isAllowed,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (isAllowed) {
            checkForModelsUsingConnector(connectorId, tenantId, actionListener);
        } else {
            actionListener.onFailure(new MLValidationException("You are not allowed to delete this connector"));
        }
    }

    private void handleConnectorAccessValidationFailure(String connectorId, Exception e, ActionListener<DeleteResponse> actionListener) {
        log.error("Failed to delete ML connector: {}", connectorId, e);
        actionListener.onFailure(e);
    }

    private void checkForModelsUsingConnector(String connectorId, String tenantId, ActionListener<DeleteResponse> actionListener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> restoringListener = ActionListener.runBefore(actionListener, context::restore);
            SearchDataObjectRequest searchRequest = buildModelSearchRequest(connectorId, tenantId);

            sdkClient
                .searchDataObjectAsync(searchRequest)
                .whenComplete(
                    (searchResponse, throwable) -> handleSearchResponse(connectorId, tenantId, restoringListener, searchResponse, throwable)
                );
        } catch (Exception e) {
            log.error("Failed to check for models using connector: {}", connectorId, e);
            actionListener.onFailure(e);
        }
    }

    private SearchDataObjectRequest buildModelSearchRequest(String connectorId, String tenantId) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.query(QueryBuilders.matchQuery(MLModel.CONNECTOR_ID_FIELD, connectorId));

        return SearchDataObjectRequest.builder().indices(ML_MODEL_INDEX).tenantId(tenantId).searchSourceBuilder(sourceBuilder).build();
    }

    private void handleSearchResponse(
        String connectorId,
        String tenantId,
        ActionListener<DeleteResponse> restoringListener,
        SearchDataObjectResponse searchResponse,
        Throwable throwable
    ) {
        if (searchResponse == null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            handleSearchFailure(connectorId, tenantId, cause, restoringListener);
            return;
        }

        try {
            SearchResponse response = searchResponse.searchResponse();
            // Parsing failure would produce NPE on next line
            SearchHit[] searchHits = response.getHits().getHits();

            if (searchHits.length == 0) {
                deleteConnector(connectorId, tenantId, restoringListener);
            } else {
                handleModelsUsingConnector(searchHits, connectorId, restoringListener);
            }
        } catch (Exception e) {
            log.error("Failed to parse search response", e);
            restoringListener.onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
        }
    }

    private void handleModelsUsingConnector(SearchHit[] searchHits, String connectorId, ActionListener<DeleteResponse> actionListener) {
        log.error("{} models are still using this connector, please delete or update the models first!", searchHits.length);
        List<String> modelIds = new ArrayList<>();
        for (SearchHit hit : searchHits) {
            modelIds.add(hit.getId());
        }
        actionListener
            .onFailure(
                new OpenSearchStatusException(
                    searchHits.length
                        + " models are still using this connector, please delete or update the models first: "
                        + Arrays.toString(modelIds.toArray(new String[0])),
                    RestStatus.CONFLICT
                )
            );
    }

    private void handleSearchFailure(String connectorId, String tenantId, Exception cause, ActionListener<DeleteResponse> actionListener) {
        if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
            deleteConnector(connectorId, tenantId, actionListener);
            return;
        }
        log.error("Failed to search for models using connector: {}", connectorId, cause);
        actionListener.onFailure(cause);
    }

    private void deleteConnector(String connectorId, String tenantId, ActionListener<DeleteResponse> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_CONNECTOR_INDEX, connectorId);
        try {
            sdkClient
                .deleteDataObjectAsync(
                    DeleteDataObjectRequest.builder().index(deleteRequest.index()).id(deleteRequest.id()).tenantId(tenantId).build()
                )
                .whenComplete((response, throwable) -> handleDeleteResponse(response, throwable, connectorId, actionListener));
        } catch (Exception e) {
            log.error("Failed to delete ML connector: {}", connectorId, e);
            actionListener.onFailure(e);
        }
    }

    private void handleDeleteResponse(
        DeleteDataObjectResponse response,
        Throwable throwable,
        String connectorId,
        ActionListener<DeleteResponse> actionListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            log.error("Failed to delete ML connector: {}", connectorId, cause);
            if (ExceptionsHelper.unwrap(cause, IndexNotFoundException.class) != null) {
                actionListener.onFailure(new OpenSearchStatusException("Failed to find connector", RestStatus.NOT_FOUND));
                return;
            }
            actionListener.onFailure(cause);
        } else {
            try {
                DeleteResponse deleteResponse = response.deleteResponse();
                log.info("Connector deletion result: {}, connector id: {}", deleteResponse.getResult(), response.id());
                actionListener.onResponse(deleteResponse);
            } catch (Exception e) {
                actionListener.onFailure(e);
            }
        }
    }
}
