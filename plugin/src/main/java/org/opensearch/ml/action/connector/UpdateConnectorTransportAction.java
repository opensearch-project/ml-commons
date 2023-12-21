/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;
import static org.opensearch.ml.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.engine.MLEngine;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateConnectorTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;

    ConnectorAccessControlHelper connectorAccessControlHelper;
    MLModelManager mlModelManager;
    MLEngine mlEngine;
    volatile List<String> trustedConnectorEndpointsRegex;

    @Inject
    public UpdateConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ConnectorAccessControlHelper connectorAccessControlHelper,
        MLModelManager mlModelManager,
        Settings settings,
        ClusterService clusterService,
        MLEngine mlEngine
    ) {
        super(MLUpdateConnectorAction.NAME, transportService, actionFilters, MLUpdateConnectorRequest::new);
        this.client = client;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
        this.mlModelManager = mlModelManager;
        this.mlEngine = mlEngine;
        trustedConnectorEndpointsRegex = ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX.get(settings);
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX, it -> trustedConnectorEndpointsRegex = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        MLUpdateConnectorRequest mlUpdateConnectorAction = MLUpdateConnectorRequest.fromActionRequest(request);
        String connectorId = mlUpdateConnectorAction.getConnectorId();

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            connectorAccessControlHelper.getConnector(client, connectorId, ActionListener.wrap(connector -> {
                boolean hasPermission = connectorAccessControlHelper.validateConnectorAccess(client, connector);
                if (Boolean.TRUE.equals(hasPermission)) {
                    connector.update(mlUpdateConnectorAction.getUpdateContent(), mlEngine::encrypt);
                    connector.validateConnectorURL(trustedConnectorEndpointsRegex);
                    UpdateRequest updateRequest = new UpdateRequest(ML_CONNECTOR_INDEX, connectorId);
                    updateRequest.doc(connector.toXContent(XContentBuilder.builder(XContentType.JSON.xContent()), ToXContent.EMPTY_PARAMS));
                    updateUndeployedConnector(connectorId, updateRequest, listener, context);
                } else {
                    listener
                        .onFailure(
                            new IllegalArgumentException("You don't have permission to update the connector, connector id: " + connectorId)
                        );
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
        UpdateRequest updateRequest,
        ActionListener<UpdateResponse> listener,
        ThreadContext.StoredContext context
    ) {
        SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchQuery(MLModel.CONNECTOR_ID_FIELD, connectorId));
        boolQueryBuilder.must(QueryBuilders.idsQuery().addIds(mlModelManager.getAllModelIds()));
        sourceBuilder.query(boolQueryBuilder);
        searchRequest.source(sourceBuilder);

        client.search(searchRequest, ActionListener.wrap(searchResponse -> {
            SearchHit[] searchHits = searchResponse.getHits().getHits();
            if (searchHits.length == 0) {
                client.update(updateRequest, getUpdateResponseListener(connectorId, listener, context));
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
        }, e -> {
            if (e instanceof IndexNotFoundException) {
                client.update(updateRequest, getUpdateResponseListener(connectorId, listener, context));
                return;
            }
            log.error("Failed to update ML connector: " + connectorId, e);
            listener.onFailure(e);

        }));
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
