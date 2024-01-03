/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateInteractionTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;

    @Inject
    public UpdateInteractionTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(UpdateInteractionAction.NAME, transportService, actionFilters, UpdateInteractionRequest::new);
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest.fromActionRequest(request);
        String interactionId = updateInteractionRequest.getInteractionId();
        UpdateRequest updateRequest = new UpdateRequest(ConversationalIndexConstants.INTERACTIONS_INDEX_NAME, interactionId);
        updateRequest.doc(updateInteractionRequest.getUpdateContent());
        updateRequest.docAsUpsert(true);
        updateRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, getUpdateResponseListener(interactionId, listener, context));
        } catch (Exception e) {
            log.error("Failed to update Interaction for interaction id " + interactionId, e);
            listener.onFailure(e);
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String interactionId,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        return ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() == DocWriteResponse.Result.UPDATED) {
                log.info("Successfully updated the interaction with ID: {}", interactionId);
                actionListener.onResponse(updateResponse);
            } else {
                log.info("Failed to update the interaction with ID: {}", interactionId);
                actionListener.onResponse(updateResponse);
            }
        }, exception -> {
            log.error("Failed to update ML interaction with ID " + interactionId, exception);
            actionListener.onFailure(exception);
        }), context::restore);
    }
}
