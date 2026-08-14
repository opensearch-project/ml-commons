/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesAction;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLListAgenticSearchTemplatesResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ListAgenticSearchTemplatesTransportAction extends
    HandledTransportAction<MLListAgenticSearchTemplatesRequest, MLListAgenticSearchTemplatesResponse> {

    private final Client client;
    private final AgenticSearchTemplateService service;

    @Inject
    public ListAgenticSearchTemplatesTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        AgenticSearchTemplateService service
    ) {
        super(MLListAgenticSearchTemplatesAction.NAME, transportService, actionFilters, MLListAgenticSearchTemplatesRequest::new);
        this.client = client;
        this.service = service;
    }

    @Override
    protected void doExecute(
        Task task,
        MLListAgenticSearchTemplatesRequest request,
        ActionListener<MLListAgenticSearchTemplatesResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            service
                .listTemplates(
                    request.getFrom(),
                    request.getSize(),
                    ActionListener
                        .wrap(
                            result -> listener.onResponse(new MLListAgenticSearchTemplatesResponse(result.templates, result.total)),
                            listener::onFailure
                        )
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
