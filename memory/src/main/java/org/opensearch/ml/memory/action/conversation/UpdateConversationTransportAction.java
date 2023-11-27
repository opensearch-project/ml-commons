/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import java.time.Instant;
import java.util.Map;

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
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateConversationTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;

    @Inject
    public UpdateConversationTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(UpdateConversationAction.NAME, transportService, actionFilters, UpdateConversationRequest::new);
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        UpdateConversationRequest updateConversationRequest = UpdateConversationRequest.fromActionRequest(request);
        String conversationId = updateConversationRequest.getConversationId();
        UpdateRequest updateRequest = new UpdateRequest(ConversationalIndexConstants.META_INDEX_NAME, conversationId);
        Map<String, Object> updateContent = updateConversationRequest.getUpdateContent();
        updateContent.putIfAbsent(ConversationalIndexConstants.META_UPDATED_FIELD, Instant.now());

        updateRequest.doc(updateContent);
        updateRequest.docAsUpsert(true);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.update(updateRequest, getUpdateResponseListener(conversationId, listener, context));
        } catch (Exception e) {
            log.error("Failed to update Conversation for conversation id {}. Details {}:", conversationId, e);
            listener.onFailure(e);
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String conversationId,
        ActionListener<UpdateResponse> actionListener,
        ThreadContext.StoredContext context
    ) {
        return ActionListener.runBefore(ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.info("Failed to update the Conversation with ID: {}", conversationId);
                actionListener.onResponse(updateResponse);
                return;
            }
            log.info("Successfully updated the Conversation with ID: {}", conversationId);
            actionListener.onResponse(updateResponse);
        }, exception -> {
            log.error("Failed to update ML Conversation with ID {}. Details: {}", conversationId, exception);
            actionListener.onFailure(exception);
        }), context::restore);
    }
}
