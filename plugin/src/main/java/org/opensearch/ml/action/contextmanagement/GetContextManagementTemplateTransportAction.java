/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLGetContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GetContextManagementTemplateTransportAction extends
    HandledTransportAction<MLGetContextManagementTemplateRequest, MLGetContextManagementTemplateResponse> {

    private final Client client;
    private final ContextManagementTemplateService contextManagementTemplateService;

    @Inject
    public GetContextManagementTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLGetContextManagementTemplateAction.NAME, transportService, actionFilters, MLGetContextManagementTemplateRequest::new);
        this.client = client;
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    @Override
    protected void doExecute(
        Task task,
        MLGetContextManagementTemplateRequest request,
        ActionListener<MLGetContextManagementTemplateResponse> listener
    ) {
        ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext();
        ActionListener<MLGetContextManagementTemplateResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
        try {
            log.debug("Getting context management template: {}", request.getTemplateName());

            contextManagementTemplateService.getTemplate(request.getTemplateName(), ActionListener.wrap(template -> {
                if (template != null) {
                    log.debug("Successfully retrieved context management template: {}", request.getTemplateName());
                    wrappedListener.onResponse(new MLGetContextManagementTemplateResponse(template));
                } else {
                    log.warn("Context management template not found: {}", request.getTemplateName());
                    wrappedListener.onFailure(new RuntimeException("Context management template not found: " + request.getTemplateName()));
                }
            }, exception -> {
                log.error("Error getting context management template: {}", request.getTemplateName(), exception);
                wrappedListener.onFailure(exception);
            }));
        } catch (Exception e) {
            log.error("Unexpected error getting context management template: {}", request.getTemplateName(), e);
            context.restore();
            listener.onFailure(e);
        }
    }
}
