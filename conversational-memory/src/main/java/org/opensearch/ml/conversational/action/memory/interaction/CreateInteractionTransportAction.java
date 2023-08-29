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
package org.opensearch.ml.conversational.action.memory.interaction;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.conversational.ConversationalMemoryHandler;
import org.opensearch.ml.conversational.index.OpenSearchConversationalMemoryHandler;
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

    /**
     * Constructor
     * @param transportService for doing intra-cluster communication
     * @param actionFilters not sure what this is for
     * @param cmHandler handler for conversational memory
     * @param client client for general opensearch ops
     */
    @Inject
    public CreateInteractionTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        Client client
    ) {
        super(CreateInteractionAction.NAME, transportService, actionFilters, CreateInteractionRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
    }

    @Override
    protected void doExecute(Task task, CreateInteractionRequest request, ActionListener<CreateInteractionResponse> actionListener) {
        String cid = request.getConversationId();
        String inp = request.getInput();
        String rsp = request.getResponse();
        String ogn = request.getOrigin();
        String prompt = request.getPromptTemplate();
        String metadata = request.getMetadata();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<CreateInteractionResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<String> al = ActionListener
                .wrap(iid -> { internalListener.onResponse(new CreateInteractionResponse(iid)); }, e -> {
                    internalListener.onFailure(e);
                });
            cmHandler.createInteraction(cid, inp, prompt, rsp, ogn, metadata, al);
        } catch (Exception e) {
            log.error(e.toString());
            actionListener.onFailure(e);
        }
    }

}
