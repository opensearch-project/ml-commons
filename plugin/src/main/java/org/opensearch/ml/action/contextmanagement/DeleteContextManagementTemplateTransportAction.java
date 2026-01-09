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
import org.opensearch.ml.common.transport.contextmanagement.MLDeleteContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLDeleteContextManagementTemplateRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLDeleteContextManagementTemplateResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteContextManagementTemplateTransportAction extends
    HandledTransportAction<MLDeleteContextManagementTemplateRequest, MLDeleteContextManagementTemplateResponse> {

    private final Client client;
    private final ContextManagementTemplateService contextManagementTemplateService;

    @Inject
    public DeleteContextManagementTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLDeleteContextManagementTemplateAction.NAME, transportService, actionFilters, MLDeleteContextManagementTemplateRequest::new);
        this.client = client;
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    @Override
    protected void doExecute(
        Task task,
        MLDeleteContextManagementTemplateRequest request,
        ActionListener<MLDeleteContextManagementTemplateResponse> listener
    ) {
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            log.info("Deleting context management template: {}", request.getTemplateName());

            contextManagementTemplateService.deleteTemplate(request.getTemplateName(), ActionListener.wrap(success -> {
                if (success) {
                    log.info("Successfully deleted context management template: {}", request.getTemplateName());
                    listener.onResponse(new MLDeleteContextManagementTemplateResponse(request.getTemplateName(), "deleted"));
                } else {
                    log.warn("Context management template not found for deletion: {}", request.getTemplateName());
                    listener.onFailure(new RuntimeException("Context management template not found: " + request.getTemplateName()));
                }
            }, exception -> {
                log.error("Error deleting context management template: {}", request.getTemplateName(), exception);
                listener.onFailure(exception);
            }));
        } catch (Exception e) {
            log.error("Unexpected error deleting context management template: {}", request.getTemplateName(), e);
            listener.onFailure(e);
        }
    }
}
