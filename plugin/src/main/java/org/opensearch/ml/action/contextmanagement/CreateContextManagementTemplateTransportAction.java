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
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLCreateContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class CreateContextManagementTemplateTransportAction extends
    HandledTransportAction<MLCreateContextManagementTemplateRequest, MLCreateContextManagementTemplateResponse> {

    private final Client client;
    private final ContextManagementTemplateService contextManagementTemplateService;

    @Inject
    public CreateContextManagementTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLCreateContextManagementTemplateAction.NAME, transportService, actionFilters, MLCreateContextManagementTemplateRequest::new);
        this.client = client;
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    @Override
    protected void doExecute(
        Task task,
        MLCreateContextManagementTemplateRequest request,
        ActionListener<MLCreateContextManagementTemplateResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            log.info("Creating context management template: {}", request.getTemplateName());

            contextManagementTemplateService.saveTemplate(request.getTemplateName(), request.getTemplate(), ActionListener.wrap(success -> {
                if (success) {
                    log.info("Successfully created context management template: {}", request.getTemplateName());
                    listener.onResponse(new MLCreateContextManagementTemplateResponse(request.getTemplateName(), "created"));
                } else {
                    log.error("Failed to create context management template: {}", request.getTemplateName());
                    listener.onFailure(new RuntimeException("Failed to create context management template"));
                }
            }, exception -> {
                log.error("Error creating context management template: {}", request.getTemplateName(), exception);
                listener.onFailure(exception);
            }));
        } catch (Exception e) {
            log.error("Unexpected error creating context management template: {}", request.getTemplateName(), e);
            listener.onFailure(e);
        }
    }
}
