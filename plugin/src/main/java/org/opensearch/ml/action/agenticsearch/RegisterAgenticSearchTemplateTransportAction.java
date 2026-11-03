/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateAction;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateRequest;
import org.opensearch.ml.common.transport.agenticsearch.MLRegisterAgenticSearchTemplateResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;
import org.opensearch.transport.client.Client;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RegisterAgenticSearchTemplateTransportAction extends
    HandledTransportAction<MLRegisterAgenticSearchTemplateRequest, MLRegisterAgenticSearchTemplateResponse> {

    private final Client client;
    private final AgenticSearchTemplateService service;

    @Inject
    public RegisterAgenticSearchTemplateTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        AgenticSearchTemplateService service
    ) {
        super(MLRegisterAgenticSearchTemplateAction.NAME, transportService, actionFilters, MLRegisterAgenticSearchTemplateRequest::new);
        this.client = client;
        this.service = service;
    }

    @Override
    protected void doExecute(
        Task task,
        MLRegisterAgenticSearchTemplateRequest request,
        ActionListener<MLRegisterAgenticSearchTemplateResponse> listener
    ) {
        // Registration reads a stored script and an index mapping on the caller's behalf, so it must run in
        // the caller's context for the security plugin to authorize those reads against the caller's own
        // permissions. The stash is therefore pushed down to the single step that needs the plugin's
        // identity - the system-index write - rather than wrapping the whole call here.
        User user = RestActionUtils.getUserContext(client);
        try {
            service
                .register(
                    request.getTemplateId(),
                    request.getIndex(),
                    request.getDescription(),
                    request.getParamSchema(),
                    user,
                    ActionListener
                        .wrap(
                            template -> listener
                                .onResponse(new MLRegisterAgenticSearchTemplateResponse(template.getTemplateId(), "created")),
                            e -> {
                                log.error("Failed to register agentic search template: {}", request.getTemplateId(), e);
                                listener.onFailure(e);
                            }
                        )
                );
        } catch (Exception e) {
            listener.onFailure(e);
        }
    }
}
