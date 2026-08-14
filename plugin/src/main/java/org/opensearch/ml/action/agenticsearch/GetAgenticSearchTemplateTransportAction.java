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
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLGetAgenticSearchTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetAgenticSearchTemplateTransportAction extends
    HandledTransportAction<MLGetAgenticSearchTemplateRequest, MLGetAgenticSearchTemplateResponse> {

    private final Client client;
    private final AgenticSearchTemplateService service;

    @Inject
    public GetAgenticSearchTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        AgenticSearchTemplateService service
    ) {
        super(MLGetAgenticSearchTemplateAction.NAME, transportService, actionFilters, MLGetAgenticSearchTemplateRequest::new);
        this.client = client;
        this.service = service;
    }

    @Override
    protected void doExecute(
        Task task,
        MLGetAgenticSearchTemplateRequest request,
        ActionListener<MLGetAgenticSearchTemplateResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            service
                .getTemplate(
                    request.getTemplateId(),
                    ActionListener
                        .wrap(template -> listener.onResponse(new MLGetAgenticSearchTemplateResponse(template)), listener::onFailure)
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
