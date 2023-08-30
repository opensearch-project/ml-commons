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

import org.opensearch.OpenSearchException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
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
    private ClusterService clusterService;

    /**
     * Constructor
     * @param transportService for doing intra-cluster communication
     * @param actionFilters for filtering actions
     * @param cmHandler handler for conversational memory
     * @param client client for general opensearch ops
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
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(Task task, CreateInteractionRequest request, ActionListener<CreateInteractionResponse> actionListener) {
        if(! clusterService.getSettings().getAsBoolean(ConversationalIndexConstants.MEMORY_FEATURE_FLAG_NAME, false)) {
            actionListener.onFailure(new OpenSearchException("The experimental Conversation Memory feature is not enabled. To enable, change the setting " + ConversationalIndexConstants.MEMORY_FEATURE_FLAG_NAME));
            return;
        }
        String cid = request.getConversationId();
        String inp = request.getInput();
        String rsp = request.getResponse();
        String ogn = request.getOrigin();
        String prompt = request.getPromptTemplate();
        String additionalInfo = request.getAdditionalInfo();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<CreateInteractionResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<String> al = ActionListener
                .wrap(iid -> { internalListener.onResponse(new CreateInteractionResponse(iid)); }, e -> {
                    internalListener.onFailure(e);
                });
            cmHandler.createInteraction(cid, inp, prompt, rsp, ogn, additionalInfo, al);
        } catch (Exception e) {
            log.error("Failed to create interaction for conversation " + cid, e);
            actionListener.onFailure(e);
        }
    }

}
