/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.model_group;

import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.action.handler.MLSearchHandler;
import org.opensearch.ml.common.transport.model_group.MLModelGroupSearchAction;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
public class SearchModelGroupTransportAction extends HandledTransportAction<SearchRequest, SearchResponse> {
    private MLSearchHandler mlSearchHandler;

    @Inject
    public SearchModelGroupTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        MLSearchHandler mlSearchHandler
    ) {
        super(MLModelGroupSearchAction.NAME, transportService, actionFilters, SearchRequest::new);
        this.mlSearchHandler = mlSearchHandler;
    }

    @Override
    protected void doExecute(Task task, SearchRequest request, ActionListener<SearchResponse> actionListener) {
        mlSearchHandler.search(request, actionListener);
    }
}
