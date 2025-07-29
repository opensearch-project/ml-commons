/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.ml.common.transport.agent.MLExecuteAgentAction;
import org.opensearch.ml.common.transport.agent.MLExecuteAgentRequest;
import org.opensearch.ml.common.transport.agent.MLExecuteAgentResponse;
import org.opensearch.ml.engine.algorithms.agent.MLAgentExecutor;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportExecuteAgentAction extends HandledTransportAction<MLExecuteAgentRequest, MLExecuteAgentResponse> {

    private MLAgentExecutor mlAgentExecutor;

    @Inject
    public TransportExecuteAgentAction(TransportService transportService, ActionFilters actionFilters, MLAgentExecutor mlAgentExecutor) {
        super(MLExecuteAgentAction.NAME, transportService, actionFilters, MLExecuteAgentRequest::new);
        this.mlAgentExecutor = mlAgentExecutor;
    }

    @Override
    protected void doExecute(Task task, MLExecuteAgentRequest request, ActionListener<MLExecuteAgentResponse> listener) {
        mlAgentExecutor.executeAgent(request.getAgentId(), request.getMethod(), request.getParameters(), listener);
    }
}
