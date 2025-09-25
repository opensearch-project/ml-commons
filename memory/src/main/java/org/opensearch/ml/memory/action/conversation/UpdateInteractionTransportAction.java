/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;

import java.time.Instant;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateInteractionTransportAction extends HandledTransportAction<ActionRequest, UpdateResponse> {
    Client client;
    private ConversationalMemoryHandler cmHandler;

    private volatile boolean featureIsEnabled;

    @Inject
    public UpdateInteractionTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        OpenSearchConversationalMemoryHandler cmHandler,
        ClusterService clusterService
    ) {
        super(UpdateInteractionAction.NAME, transportService, actionFilters, UpdateInteractionRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
        this.featureIsEnabled = MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.get(clusterService.getSettings());
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED, it -> featureIsEnabled = it);
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<UpdateResponse> listener) {
        if (!featureIsEnabled) {
            listener.onFailure(new OpenSearchException(ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE));
            return;
        }
        UpdateInteractionRequest updateInteractionRequest = UpdateInteractionRequest.fromActionRequest(request);
        String interactionId = updateInteractionRequest.getInteractionId();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            Map<String, Object> updateContent = updateInteractionRequest.getUpdateContent();
            updateContent.putIfAbsent(ConversationalIndexConstants.INTERACTIONS_UPDATED_TIME_FIELD, Instant.now());

            cmHandler.updateInteraction(interactionId, updateContent, getUpdateResponseListener(interactionId, listener, context));
        } catch (Exception e) {
            log.error("Failed to update Message with ID " + interactionId, e);
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
                log.info("Successfully updated the Message with ID: {}", interactionId);
                actionListener.onResponse(updateResponse);
            } else {
                log.info("Failed to update the Message with ID: {}", interactionId);
                actionListener.onResponse(updateResponse);
            }
        }, exception -> {
            log.error("Failed to update ML Message with ID " + interactionId, exception);
            actionListener.onFailure(exception);
        }), context::restore);
    }
}
