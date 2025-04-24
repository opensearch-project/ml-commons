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

import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE;

import java.util.List;

import org.opensearch.OpenSearchException;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationMeta;
import org.opensearch.ml.common.settings.MLCommonsSettings;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

/**
 * ListConversationsAction that does the work of asking for them from the ConversationalMemoryHandler
 */
@Log4j2
public class GetConversationsTransportAction extends HandledTransportAction<GetConversationsRequest, GetConversationsResponse> {

    private final Client client;
    private final ConversationalMemoryHandler cmHandler;

    private volatile boolean featureIsEnabled;

    /**
     * Constructor
     * @param transportService for inter-node communications
     * @param actionFilters for filtering actions
     * @param cmHandler Handler for conversational memory operations
     * @param client OS Client for dealing with OS
     * @param clusterService for some cluster ops
     */
    @Inject
    public GetConversationsTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        Client client,
        ClusterService clusterService
    ) {
        super(GetConversationsAction.NAME, transportService, actionFilters, GetConversationsRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
        this.featureIsEnabled = MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED.get(clusterService.getSettings());
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(MLCommonsSettings.ML_COMMONS_MEMORY_FEATURE_ENABLED, it -> featureIsEnabled = it);
    }

    @Override
    public void doExecute(Task task, GetConversationsRequest request, ActionListener<GetConversationsResponse> actionListener) {
        if (!featureIsEnabled) {
            actionListener.onFailure(new OpenSearchException(ML_COMMONS_MEMORY_FEATURE_DISABLED_MESSAGE));
            return;
        }
        int maxResults = request.getMaxResults();
        int from = request.getFrom();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<GetConversationsResponse> internalListener = ActionListener.runBefore(actionListener, context::restore);
            ActionListener<List<ConversationMeta>> al = ActionListener.wrap(conversations -> {
                internalListener
                    .onResponse(new GetConversationsResponse(conversations, from + maxResults, conversations.size() == maxResults));
            }, e -> {
                log.error("Failed to get memories", e);
                internalListener.onFailure(e);
            });
            cmHandler.getConversations(from, maxResults, al);
        } catch (Exception e) {
            log.error("Failed to get memories", e);
            actionListener.onFailure(e);
        }
    }
}
