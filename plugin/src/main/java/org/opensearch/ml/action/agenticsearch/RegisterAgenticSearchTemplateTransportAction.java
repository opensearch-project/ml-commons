/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agenticsearch;

import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
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
        // Capture the caller's identity BEFORE stashing: stashContext() installs a
        // fresh context that drops the security-user transient, so reading it after the
        // stash (here or in the service) would always yield null. Pass it into the
        // service so created_by records the real registering user.
        User user = RestActionUtils.getUserContext(client);
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            service
                .register(
                    request.getTemplateId(),
                    request.getIndex(),
                    request.getDescription(),
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
