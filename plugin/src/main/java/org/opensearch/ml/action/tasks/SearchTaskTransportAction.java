/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.tasks;

import static org.opensearch.ml.utils.RestActionUtils.wrapListenerToHandleConnectorIndexNotFound;

import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.task.MLTaskSearchAction;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class SearchTaskTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private Client client;

    @Inject
    public SearchTaskTransportAction(TransportService transportService, ActionFilters actionFilters, Client client) {
        super(MLTaskSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.client = client;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            final ActionListener<SearchResponse> wrappedListener = ActionListener
                .wrap(actionListener::onResponse, e -> wrapListenerToHandleConnectorIndexNotFound(e, actionListener));
            client.search(request, ActionListener.runBefore(wrappedListener, () -> context.restore()));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            actionListener.onFailure(e);
        }
    }
}
