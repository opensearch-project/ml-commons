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
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLDeleteAgenticSearchTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteAgenticSearchTemplateTransportAction extends
    HandledTransportAction<MLDeleteAgenticSearchTemplateRequest, MLDeleteAgenticSearchTemplateResponse> {

    private final Client client;
    private final AgenticSearchTemplateService service;

    @Inject
    public DeleteAgenticSearchTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        AgenticSearchTemplateService service
    ) {
        super(MLDeleteAgenticSearchTemplateAction.NAME, transportService, actionFilters, MLDeleteAgenticSearchTemplateRequest::new);
        this.client = client;
        this.service = service;
    }

    @Override
    protected void doExecute(
        Task task,
        MLDeleteAgenticSearchTemplateRequest request,
        ActionListener<MLDeleteAgenticSearchTemplateResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            service
                .deleteTemplate(
                    request.getTemplateId(),
                    ActionListener
                        .wrap(
                            deleted -> listener.onResponse(new MLDeleteAgenticSearchTemplateResponse(request.getTemplateId(), "deleted")),
                            listener::onFailure
                        )
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
