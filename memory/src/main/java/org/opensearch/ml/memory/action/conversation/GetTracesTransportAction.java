/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.memory.action.conversation;

import java.util.List;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.conversation.Interaction;
import org.opensearch.ml.memory.ConversationalMemoryHandler;
import org.opensearch.ml.memory.index.OpenSearchConversationalMemoryHandler;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetTracesTransportAction extends HandledTransportAction<GetTracesRequest, GetTracesResponse> {
    private Client client;
    private ConversationalMemoryHandler cmHandler;

    /**
     * Constructor
     * @param transportService for inter-node communications
     * @param actionFilters for filtering actions
     * @param cmHandler Handler for conversational memory operations
     * @param client OS Client for dealing with OS
     * @param clusterService for some cluster ops
     */
    @Inject
    public GetTracesTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        OpenSearchConversationalMemoryHandler cmHandler,
        Client client,
        ClusterService clusterService
    ) {
        super(GetTracesAction.NAME, transportService, actionFilters, GetTracesRequest::new);
        this.client = client;
        this.cmHandler = cmHandler;
    }

    @Override
    public void doExecute(Task task, GetTracesRequest request, ActionListener<GetTracesResponse> actionListener) {
        int maxResults = request.getMaxResults();
        int from = request.getFrom();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().newStoredContext(true)) {
            ActionListener<GetTracesResponse> internalListener = ActionListener.runBefore(actionListener, () -> context.restore());
            ActionListener<List<Interaction>> al = ActionListener
                .wrap(traces -> { internalListener.onResponse(new GetTracesResponse(traces)); }, e -> {
                    internalListener.onFailure(e);
                });
            cmHandler.getTraces(request.getInteractionId(), from, maxResults, al);
        } catch (Exception e) {
            log.error("Failed to get traces for conversation " + request.getInteractionId(), e);
            actionListener.onFailure(e);
        }
    }
}
