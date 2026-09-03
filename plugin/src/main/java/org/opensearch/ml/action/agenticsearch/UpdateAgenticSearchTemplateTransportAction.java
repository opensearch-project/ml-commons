/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLUpdateAgenticSearchTemplateRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateAgenticSearchTemplateTransportAction extends
    HandledTransportAction<MLUpdateAgenticSearchTemplateRequest, UpdateResponse> {

    private final Client client;
    private final AgenticSearchTemplateService service;

    @Inject
    public UpdateAgenticSearchTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        AgenticSearchTemplateService service
    ) {
        super(MLUpdateAgenticSearchTemplateAction.NAME, transportService, actionFilters, MLUpdateAgenticSearchTemplateRequest::new);
        this.client = client;
        this.service = service;
    }

    @Override
    protected void doExecute(Task task, MLUpdateAgenticSearchTemplateRequest request, ActionListener<UpdateResponse> listener) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            service.updateTemplate(request.getTemplateId(), request.getTemplate(), listener);
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
