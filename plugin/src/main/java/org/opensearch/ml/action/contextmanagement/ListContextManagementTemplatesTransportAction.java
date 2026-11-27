/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.contextmanagement;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesAction;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesRequest;
import org.opensearch.ml.common.transport.contextmanagement.MLListContextManagementTemplatesResponse;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class ListContextManagementTemplatesTransportAction extends
    HandledTransportAction<MLListContextManagementTemplatesRequest, MLListContextManagementTemplatesResponse> {

    private final Client client;
    private final ContextManagementTemplateService contextManagementTemplateService;

    @Inject
    public ListContextManagementTemplatesTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ContextManagementTemplateService contextManagementTemplateService
    ) {
        super(MLListContextManagementTemplatesAction.NAME, transportService, actionFilters, MLListContextManagementTemplatesRequest::new);
        this.client = client;
        this.contextManagementTemplateService = contextManagementTemplateService;
    }

    @Override
    protected void doExecute(
        Task task,
        MLListContextManagementTemplatesRequest request,
        ActionListener<MLListContextManagementTemplatesResponse> listener
    ) {
        try {
            log.debug("Listing context management templates from: {} size: {}", request.getFrom(), request.getSize());

            contextManagementTemplateService.listTemplates(request.getFrom(), request.getSize(), ActionListener.wrap(result -> {
                log.debug("Successfully retrieved {} of {} context management templates", result.getTemplates().size(), result.getTotal());
                listener.onResponse(new MLListContextManagementTemplatesResponse(result.getTemplates(), result.getTotal()));
            }, exception -> {
                log.error("Error listing context management templates", exception);
                listener.onFailure(exception);
            }));
        } catch (Exception e) {
            log.error("Unexpected error listing context management templates", e);
            listener.onFailure(e);
        }
    }
}
