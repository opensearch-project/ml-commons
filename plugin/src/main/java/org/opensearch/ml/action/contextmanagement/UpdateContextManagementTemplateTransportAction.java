/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.contextmanagement.MLUpdateContextManagementTemplateAction;
import org.opensearch.ml.common.transport.contextmanagement.MLUpdateContextManagementTemplateRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class UpdateContextManagementTemplateTransportAction extends
    HandledTransportAction<MLUpdateContextManagementTemplateRequest, UpdateResponse> {

    private final Client client;
    private final ContextManagementTemplateService contextManagementTemplateService;

    @Inject
    public UpdateContextManagementTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLUpdateContextManagementTemplateAction.NAME, transportService, actionFilters, MLUpdateContextManagementTemplateRequest::new);
        this.client = client;
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    @Override
    protected void doExecute(Task task, MLUpdateContextManagementTemplateRequest request, ActionListener<UpdateResponse> listener) {
        ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext();
        ActionListener<UpdateResponse> wrappedListener = ActionListener.runBefore(listener, context::restore);
        try {
            log.info("Updating context management template: {}", request.getTemplateName());

            contextManagementTemplateService.updateTemplate(request.getTemplateName(), request.getTemplate(), wrappedListener);
        } catch (Exception e) {
            log.error("Failed to update context management template: {}", request.getTemplateName(), e);
            context.restore();
            listener.onFailure(e);
        }
    }
}
