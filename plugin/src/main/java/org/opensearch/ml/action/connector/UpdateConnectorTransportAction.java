/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.GENERAL_THREAD_POOL;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.utils.TenantAwareHelper;
import org.opensearch.sdk.GetDataObjectRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.sdk.SdkClientUtils;
import org.opensearch.sdk.SearchDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectRequest;
import org.opensearch.sdk.UpdateDataObjectResponse;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.FetchSourceContext;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateConnectorTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    final Client client;
    final SdkClient sdkClient;

    final ConnectorAccessControlHelper connectorAccessControlHelper;
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;
    final MLModelManager mlModelManager;
    final MLEngine mlEngine;
    volatile List<String> trustedConnectorEndpointsRegex;

    @Inject
    public UpdateConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLModelManager mlModelManager,
        Settings settings,
        ClusterService clusterService,
        MLEngine mlEngine,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        super(MLUpdateConnectorAction.NAME, transportService, actionFilters, MLUpdateConnectorRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlModelManager = mlModelManager;
        this.mlEngine = mlEngine;
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
        trustedConnectorEndpointsRegex = ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX, it -> trustedConnectorEndpointsRegex = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        MLUpdateConnectorRequest mlUpdateConnectorAction = MLUpdateConnectorRequest.fromActionRequest(request);
        MLCreateConnectorInput mlCreateConnectorInput = mlUpdateConnectorAction.getUpdateContent();
        if (!TenantAwareHelper.validateTenantId(mlFeatureEnabledSetting, mlCreateConnectorInput.getTenantId(), listener)) {
            return;
        }
        String connectorId = mlUpdateConnectorAction.getConnectorId();
        FetchSourceContext fetchSourceContext = new FetchSourceContext(true, Strings.EMPTY_ARRAY, Strings.EMPTY_ARRAY);
        GetDataObjectRequest getDataObjectRequest = GetDataObjectRequest
            .builder()
            .index(ML_CONNECTOR_INDEX)
            .id(connectorId)
            .fetchSourceContext(fetchSourceContext)
            .build();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            connectorAccessControlHelper
                .getConnector(sdkClient, client, context, getDataObjectRequest, connectorId, ActionListener.wrap(connector -> {
                    if (TenantAwareHelper
                        .validateTenantResource(
                            mlFeatureEnabledSetting,
                            mlCreateConnectorInput.getTenantId(),
                            connector.getTenantId(),
                            listener
                        )) {
                        boolean hasPermission = connectorAccessControlHelper.validateConnectorAccess(client, connector);
                        if (Boolean.TRUE.equals(hasPermission)) {
                            connector.update(mlUpdateConnectorAction.getUpdateContent(), mlEngine::encrypt);
                            connector.validateConnectorURL(trustedConnectorEndpointsRegex);
                            UpdateDataObjectRequest updateDataObjectRequest = UpdateDataObjectRequest
                                .builder()
                                .index(ML_CONNECTOR_INDEX)
                                .id(connectorId)
                                .dataObject(connector)
                                .build();
                            updateUndeployedConnector(connectorId, updateDataObjectRequest, listener, context);
                        } else {
                            listener
                                .onFailure(
                                    new IllegalArgumentException(
                                        "You don't have permission to update the connector, connector id: " + connectorId
                                    )
                                );
                        }
                    }
                }, exception -> {
                    log.error("Permission denied: Unable to update the connector with ID {}. Details: {}", connectorId, exception);
                    listener.onFailure(exception);
                }));
        } catch (Exception e) {
            log.error("Failed to update ML connector for connector id {}. Details {}:", connectorId, e);
            listener.onFailure(e);
        }
    }

    private void updateUndeployedConnector(
        String connectorId,
        UpdateDataObjectRequest updateDataObjectRequest,
        ActionListener<UpdateResponse> listener,
        ThreadContext.StoredContext context
    ) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchQuery(MLModel.CONNECTOR_ID_FIELD, connectorId));
        boolQueryBuilder.must(QueryBuilders.idsQuery().addIds(mlModelManager.getAllModelIds()));
        sourceBuilder.query(boolQueryBuilder);

        SearchDataObjectRequest searchDataObjectRequest = SearchDataObjectRequest
            .builder()
            .indices(ML_MODEL_INDEX)
            .searchSourceBuilder(sourceBuilder)
            .build();
        sdkClient
            .searchDataObjectAsync(searchDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
            .whenComplete((sr, st) -> {
                if (sr != null) {
                    try {
                        SearchResponse searchResponse = SearchResponse.fromXContent(sr.parser());
                        SearchHit[] searchHits = searchResponse.getHits().getHits();
                        if (searchHits.length == 0) {
                            sdkClient
                                .updateDataObjectAsync(updateDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                                .whenComplete((r, throwable) -> {
                                    handleUpdateDataObjectCompletionStage(
                                        r,
                                        throwable,
                                        getUpdateResponseListener(connectorId, listener, context)
                                    );
                                });
                        } else {
                            log.error(searchHits.length + " models are still using this connector, please undeploy the models first!");
                            List<String> modelIds = new ArrayList<>();
                            for (SearchHit hit : searchHits) {
                                modelIds.add(hit.getId());
                            }
                            listener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        searchHits.length
                                            + " models are still using this connector, please undeploy the models first: "
                                            + Arrays.toString(modelIds.toArray(new String[0])),
                                        RestStatus.BAD_REQUEST
                                    )
                                );
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse search response", e);
                        listener
                            .onFailure(new OpenSearchStatusException("Failed to parse search response", RestStatus.INTERNAL_SERVER_ERROR));
                    }
                } else {
                    Exception cause = SdkClientUtils.unwrapAndConvertToException(st);
                    log.error("Failed to update ML connector: " + connectorId, cause);
                    if (cause instanceof IndexNotFoundException) {
                        sdkClient
                            .updateDataObjectAsync(updateDataObjectRequest, client.threadPool().executor(GENERAL_THREAD_POOL))
                            .whenComplete((r, throwable) -> {
                                handleUpdateDataObjectCompletionStage(
                                    r,
                                    throwable,
                                    getUpdateResponseListener(connectorId, listener, context)
                                );
                            });
                        return;
                    } else {
                        listener.onFailure(cause);
                    }
                }
            });
    }

    private void handleUpdateDataObjectCompletionStage(
        UpdateDataObjectResponse r,
        Throwable throwable,
        ActionListener<UpdateResponse> updateListener
    ) {
        if (throwable != null) {
            Exception cause = SdkClientUtils.unwrapAndConvertToException(throwable);
            updateListener.onFailure(cause);
        } else {
            try {
                UpdateResponse updateResponse = r.parser() == null ? null : UpdateResponse.fromXContent(r.parser());
                updateListener.onResponse(updateResponse);
            } catch (IOException e) {
                updateListener.onFailure(e);
            }
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String connectorId,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        return ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.error("Failed to update the connector with ID: {}", connectorId);
                actionListener.onResponse(updateResponse);
                return;
            }
            log.info("Successfully updated the connector with ID: {}", connectorId);
            actionListener.onResponse(updateResponse);
        }, exception -> {
            log.error("Failed to update ML connector with ID {}. Details: {}", connectorId, exception);
            actionListener.onFailure(exception);
        }), context::restore);
    }
}
