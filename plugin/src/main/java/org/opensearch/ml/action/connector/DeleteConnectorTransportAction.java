/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;
import static org.opensearch.ml.common.CommonValue.ML_MODEL_INDEX;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.exception.MLValidationException;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteAction;
import org.opensearch.ml.common.transport.connector.MLConnectorDeleteRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteConnectorTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public DeleteConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLConnectorDeleteAction.NAME, transportService, actionFilters, MLConnectorDeleteRequest::new);
        this.client = client;
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
                            actionListener
                                .onFailure(
                                    new MLValidationException(
                                        searchHits.length
                                            + " models are still using this connector, please delete or update the models first!"
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
            client.delete(deleteRequest, ActionListener.runBefore(new ActionListener<>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {
                    if (deleteResponse.getResult() == DocWriteResponse.Result.NOT_FOUND) {
                        log.info("Connector id:{} not found", connectorId);
                        actionListener.onResponse(deleteResponse);
                        return;
                    }
                    log.info("Completed Delete Connector Request, connector id:{} deleted", connectorId);
                    actionListener.onResponse(deleteResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete ML connector: " + connectorId, e);
                    actionListener.onFailure(e);
                }
            }, () -> context.restore()));
        } catch (Exception e) {
            log.error("Failed to delete ML connector: " + connectorId, e);
            actionListener.onFailure(e);
        }
    }
}
