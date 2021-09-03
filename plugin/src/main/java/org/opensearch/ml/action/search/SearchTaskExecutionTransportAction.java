/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.search;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.search.SearchTaskRequest;
import org.opensearch.ml.common.transport.search.SearchTaskResponse;
import org.opensearch.ml.task.SearchTaskRunner;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SearchTaskExecutionTransportAction extends HandledTransportAction<SearchTaskRequest, SearchTaskResponse> {
    SearchTaskRunner searchTaskRunner;

    @Inject
    public SearchTaskExecutionTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        SearchTaskRunner searchTaskRunner
    ) {
        super(SearchTaskExecutionAction.NAME, transportService, actionFilters, SearchTaskRequest::new);
        this.searchTaskRunner = searchTaskRunner;
    }

    @Override
    protected void doExecute(Task task, SearchTaskRequest request, ActionListener<SearchTaskResponse> listener) {
        searchTaskRunner.startSearchTask(request, listener);
    }
}
