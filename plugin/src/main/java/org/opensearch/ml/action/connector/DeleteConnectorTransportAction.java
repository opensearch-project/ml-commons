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

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
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
import org.opensearch.sdk.DeleteCustomRequest;
import org.opensearch.sdk.SdkClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteConnectorTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    private final SdkClient sdkClient;
    NamedXContentRegistry xContentRegistry;

    ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public DeleteConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        SdkClient sdkClient,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLConnectorDeleteAction.NAME, transportService, actionFilters, MLConnectorDeleteRequest::new);
        this.client = client;
        this.sdkClient = sdkClient;
        this.xContentRegistry = xContentRegistry;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLConnectorDeleteRequest mlConnectorDeleteRequest = MLConnectorDeleteRequest.fromActionRequest(request);
        String connectorId = mlConnectorDeleteRequest.getConnectorId();
        DeleteRequest deleteRequest = new DeleteRequest(ML_CONNECTOR_INDEX, connectorId);
        connectorAccessControlHelper.validateConnectorAccess(client, connectorId, ActionListener.wrap(x -> {
            if (Boolean.TRUE.equals(x)) {
                try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
                    SearchRequest searchRequest = new SearchRequest(ML_MODEL_INDEX);
                    SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
                    sourceBuilder.query(QueryBuilders.matchQuery(MLModel.CONNECTOR_ID_FIELD, connectorId));
                    searchRequest.source(sourceBuilder);
                    client.search(searchRequest, ActionListener.runBefore(ActionListener.wrap(searchResponse -> {
                        SearchHit[] searchHits = searchResponse.getHits().getHits();
                        if (searchHits.length == 0) {
                            deleteConnector(deleteRequest, connectorId, actionListener);
                        } else {
                            log
                                .error(
                                    searchHits.length + " models are still using this connector, please delete or update the models first!"
                                );
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
                    }, e -> {
                        if (e instanceof IndexNotFoundException) {
                            deleteConnector(deleteRequest, connectorId, actionListener);
                            return;
                        }
                        log.error("Failed to delete ML connector: " + connectorId, e);
                        actionListener.onFailure(e);
                    }), () -> context.restore()));
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    actionListener.onFailure(e);
                }
            } else {
                actionListener.onFailure(new MLValidationException("You are not allowed to delete this connector"));
            }
        }, e -> {
            log.error("Failed to delete ML connector: " + connectorId, e);
            actionListener.onFailure(e);
        }));
    }

    private void deleteConnector(DeleteRequest deleteRequest, String connectorId, ActionListener<DeleteResponse> actionListener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            sdkClient
                .deleteCustomAsync(new DeleteCustomRequest.Builder().index(deleteRequest.index()).id(deleteRequest.id()).build())
                .whenCompleteAsync((r, throwable) -> {
                    if (throwable != null) {
                        actionListener.onFailure(new RuntimeException(throwable));
                    } else {
                        context.restore();
                        log.info("Connector deletion result: {}, connector id: {}", r.deleted(), r.id());
                        DeleteResponse response = new DeleteResponse(deleteRequest.shardId(), r.id(), 0, 0, 0, r.deleted());
                        actionListener.onResponse(response);
                    }
                });
        } catch (Exception e) {
            log.error("Failed to delete ML connector: " + connectorId, e);
            actionListener.onFailure(e);
        }
    }
}
