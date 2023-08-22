/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.connector;

import static org.opensearch.ml.common.CommonValue.ML_CONNECTOR_INDEX;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorAction;
import org.opensearch.ml.common.transport.connector.MLUpdateConnectorRequest;
import org.opensearch.ml.helper.ConnectorAccessControlHelper;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class UpdateConnectorTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;

    ConnectorAccessControlHelper connectorAccessControlHelper;

    @Inject
    public UpdateConnectorTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ConnectorAccessControlHelper connectorAccessControlHelper
    ) {
        super(MLUpdateConnectorAction.NAME, transportService, actionFilters, MLUpdateConnectorRequest::new);
        this.client = client;
        this.connectorAccessControlHelper = connectorAccessControlHelper;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        MLUpdateConnectorRequest mlUpdateConnectorAction = MLUpdateConnectorRequest.fromActionRequest(request);
        String connectorId = mlUpdateConnectorAction.getConnectorId();
        UpdateRequest updateRequest = new UpdateRequest(ML_CONNECTOR_INDEX, connectorId);
        updateRequest.doc(mlUpdateConnectorAction.getUpdateContent());
        updateRequest.docAsUpsert(true);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            connectorAccessControlHelper.validateConnectorAccess(client, connectorId, ActionListener.wrap(hasPermission -> {
                if (Boolean.TRUE.equals(hasPermission)) {
                    client.update(updateRequest, getUpdateResponseListener(connectorId, listener, context));
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

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String connectorId,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        return ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.info("Failed to update the connector with ID: {}", connectorId);
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
