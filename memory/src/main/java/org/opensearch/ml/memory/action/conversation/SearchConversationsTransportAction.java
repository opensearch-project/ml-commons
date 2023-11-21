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
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class SearchConversationsTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {

    private ConversationalMemoryHandler cmHandler;

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
    public SearchConversationsTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        ClusterService clusterService
    ) {
        super(SearchConversationsAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.cmHandler = cmHandler;
        this.featureIsEnabled = ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.get(clusterService.getSettings());
        clusterService
            .getClusterSettings()
            .addSettingsUpdateConsumer(ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED, it -> featureIsEnabled = it);
    }

    @Override
    public void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        if (!featureIsEnabled) {
            actionListener
                .onFailure(
                    new OpenSearchException(
                        "The experimental Conversation Memory feature is not enabled. To enable, please update the setting "
                            + ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED.getKey()
                    )
                );
            return;
        } else {
            cmHandler.searchConversations(request, actionListener);
        }
    }
}
