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
package org.opensearch.ml.conversational.action.memory.conversation;

import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.conversational.ConversationalMemoryHandler;
import org.opensearch.ml.conversational.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * Transport action that does all the work for DeleteConversation
 */
@Log4j2
public class DeleteConversationTransportAction extends HandledTransportAction<DeleteConversationRequest, DeleteConversationResponse> {

    private Client client;
    private ConversationalMemoryHandler cmHandler;

    /**
     * Constructor
     * @param transportService for inter-node communications
     * @param actionFilters not sure what this is for tbh
     * @param cmHandler Handler for conversational memory operations
     * @param client OS Client for dealing with OS
     */
    @Inject
    public DeleteConversationTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler, 
        Client client
    ) {
        super(DeleteConversationAction.NAME, transportService, actionFilters, DeleteConversationRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
    }

    @Override
    public void doExecute(Task task, DeleteConversationRequest request, ActionListener<DeleteConversationResponse> listener) {
        String conversationId = request.getId();
        try(ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteConversationResponse> internalListener = ActionListener.runBefore(listener, () -> context.restore());
            ActionListener<Boolean> al = ActionListener.wrap(success -> {
                DeleteConversationResponse response = new DeleteConversationResponse(success);
                internalListener.onResponse(response);
            }, e -> {
                internalListener.onFailure(e);
            });
            cmHandler.deleteConversation(conversationId, al);
        } catch (Exception e) {
            log.error(e.toString());
            listener.onFailure(e);
        }
    }
}