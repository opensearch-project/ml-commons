/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.DeleteDataObjectRequest;
import org.opensearch.sdk.DeleteDataObjectResponse;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

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
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
            sourceBuilder.query(QueryBuilders.matchQuery(MLModel.CONNECTOR_ID_FIELD, connectorId));
            if (mlFeatureEnabledSetting.isMultiTenancyEnabled()) {
                sourceBuilder.query(QueryBuilders.matchQuery(TENANT_ID, tenantId));
            }

            SearchDataObjectRequest searchDataObjectRequest = new SearchDataObjectRequest.Builder()
                .indices(ML_MODEL_INDEX)
                .searchSourceBuilder(sourceBuilder)
                .build();
            sdkClient
                .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                .whenComplete((sr, st) -> {
                    context.restore();
                    if (sr != null) {
                        try {
                            SearchResponse searchResponse = SearchResponse.fromXContent(sr.parser());
                            SearchHit[] searchHits = searchResponse.getHits().getHits();
                            if (searchHits.length == 0) {
                                deleteConnector(connectorId, actionListener);
                            } else {
                                handleModelsUsingConnector(searchHits, connectorId, actionListener);
                            }
                        } catch (Exception e) {
                            log.error("Failed to parse search response", e);
                            actionListener
                                .onFailure(
                                    new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR)
                                );
                        }
                    } else {
                        Exception cause = SdkClientUtils.unwrapAndConvertToException(st);
                        handleSearchFailure(connectorId, cause, actionListener);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to check for models using connector: {}", connectorId, e);
            actionListener.onFailure(e);
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

    private void handleSearchFailure(String connectorId, Exception cause, ActionListener<DeleteResponse> actionListener) {
        if (cause instanceof IndexNotFoundException) {
            deleteConnector(connectorId, actionListener);
            return;
        }
        log.error("Failed to search for models using connector: {}", connectorId, cause);
        actionListener.onFailure(cause);
    }

    private void deleteConnector(String connectorId, ActionListener<DeleteResponse> actionListener) {
        DeleteRequest deleteRequest = new DeleteRequest(ML_CONNECTOR_INDEX, connectorId);
        try {
            sdkClient
                .deleteDataObjectAsync(
                    new DeleteDataObjectRequest.Builder().index(deleteRequest.index()).id(deleteRequest.id()).build(),
                    client.threadPool().executor(GENERAL_THREAD_POOL)
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
            actionListener.onFailure(cause);
        } else {
            log.info("Connector deletion result: {}, connector id: {}", response.deleted(), response.id());
            DeleteResponse deleteResponse = new DeleteResponse(response.shardId(), response.id(), 0, 0, 0, response.deleted());
            deleteResponse.setShardInfo(response.shardInfo());
            actionListener.onResponse(deleteResponse);
        }
    }
}
