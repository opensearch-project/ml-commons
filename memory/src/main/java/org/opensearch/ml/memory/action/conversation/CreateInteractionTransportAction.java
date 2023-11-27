/*
 * Copyright 2023 Aryn
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opensearch.ml.memory.action.conversation;

import java.util.HashMap;
import java.util.Map;

import org.opensearch.OpenSearchException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * The put interaction action that does the work (of calling cmHandler)
 */
@Log4j2
public class CreateInteractionTransportAction extends HandledTransportAction<CreateInteractionRequest, CreateInteractionResponse> {

    private ConversationalMemoryHandler cmHandler;
    private Client client;

    private volatile boolean featureIsEnabled;

    /**
     * Constructor
     * @param transportService for doing intra-cluster communication
     * @param actionFilters for filtering actions
     * @param cmHandler handler for conversational memory
     * @param client client for general opensearch ops
     * @param clusterService for some cluster ops
     */
    @Inject
    public CreateInteractionTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        Client client,
        ClusterService clusterService
    ) {
        super(CreateInteractionAction.NAME, transportService, actionFilters, CreateInteractionRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
        this.featureIsEnabled = ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.get(clusterService.getSettings());
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED, it -> featureIsEnabled = it);
    }

    @Override
    protected void doExecute(Task task, CreateInteractionRequest request, ActionListener<CreateInteractionResponse> actionListener) {
        if (!featureIsEnabled) {
            actionListener
                .onFailure(
                    new OpenSearchException(
                        "The experimental Conversation Memory feature is not enabled. To enable, please update the setting "
                            + ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey()
                    )
                );
            return;
        }
        String cid = request.getConversationId();
        String inp = request.getInput();
        String rsp = request.getResponse();
        String ogn = request.getOrigin();
        String prompt = request.getPromptTemplate();
        Map<String, String> additionalInfo = request.getAdditionalInfo();
        String parentId = request.getParentInteractionId();
        Integer traceNumber = request.getTraceNum();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<CreateInteractionResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<String> al = ActionListener.wrap(iid -> {
                cmHandler.updateConversation(cid, new HashMap<>(), getUpdateResponseListener(cid, iid, internalListener));
                // internalListener.onResponse(new CreateInteractionResponse(iid));
            }, e -> { internalListener.onFailure(e); });
            if (parentId == null || traceNumber == null) {
                cmHandler.createInteraction(cid, inp, prompt, rsp, ogn, additionalInfo, al);
            } else {
                cmHandler.createInteraction(cid, inp, prompt, rsp, ogn, additionalInfo, al, parentId, traceNumber);
            }
        } catch (Exception e) {
            log.error("Failed to create interaction for conversation " + cid, e);
            actionListener.onFailure(e);
        }
    }

    private ActionListener<UpdateResponse> getUpdateResponseListener(
        String conversationId,
        String interactionId,
        ActionListener<CreateInteractionResponse> actionListener
    ) {
        return ActionListener.wrap(updateResponse -> {
            if (updateResponse != null && updateResponse.getResult() != DocWriteResponse.Result.UPDATED) {
                log.info("Failed to update the Conversation with ID: {} after interaction {} is created", conversationId, interactionId);
                actionListener.onResponse(new CreateInteractionResponse(interactionId));
                return;
            }
            log.info("Successfully updated the Conversation with ID: {} after interaction {} is created", conversationId);
            actionListener.onResponse(new CreateInteractionResponse(interactionId));
        }, exception -> {
            log
                .error(
                    "Failed to update Conversation with ID {} after interaction {} is created. Details: {}",
                    conversationId,
                    interactionId,
                    exception
                );
            actionListener.onResponse(new CreateInteractionResponse(interactionId));
        });

    }
}
