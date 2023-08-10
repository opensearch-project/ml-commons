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

import java.util.List;

import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.ml.conversational.ConversationalMemoryHandler;
import org.opensearch.ml.conversational.index.ConversationMeta;
import org.opensearch.ml.conversational.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * ListConversationsAction that does the work of asking for them from the ConversationalMemoryHandler
 */
@Log4j2
public class GetConversationsTransportAction extends HandledTransportAction<GetConversationsRequest, GetConversationsResponse> {

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
    public GetConversationsTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler, 
        Client client
    ) {
        super(GetConversationsAction.NAME, transportService, actionFilters, GetConversationsRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
    }

    @Override
    public void doExecute(Task task, GetConversationsRequest request, ActionListener<GetConversationsResponse> actionListener) {
        int maxResults = request.getMaxResults();
        int from = request.getFrom();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<GetConversationsResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<List<ConversationMeta>> al = ActionListener.wrap(conversations -> {
                internalListener.onResponse(new GetConversationsResponse(conversations, from + maxResults, conversations.size() == maxResults));
            }, e -> {
                log.error(e.toString());
                internalListener.onFailure(e);
            });
            cmHandler.getConversations(from, maxResults, al);
        } catch (Exception e) {
            log.error(e.toString());
            actionListener.onFailure(e);
        }
    }
}