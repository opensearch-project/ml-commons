/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;

import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteAgentTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    @Inject
    public DeleteAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLAgentDeleteAction.NAME, transportService, actionFilters, MLAgentDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLAgentDeleteRequest mlAgentDeleteRequest = MLAgentDeleteRequest.fromActionRequest(request);
        String agentId = mlAgentDeleteRequest.getAgentId();
        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            ActionListener<DeleteResponse> wrappedListener = ActionListener.runBefore(actionListener, () -> context.restore());
            DeleteRequest deleteRequest = new DeleteRequest(ML_AGENT_INDEX, agentId);
            client.delete(deleteRequest, new ActionListener<DeleteResponse>() {
                @Override
                public void onResponse(DeleteResponse deleteResponse) {
                    log.debug("Completed Delete Agent Request, agent id:{} deleted", agentId);
                    wrappedListener.onResponse(deleteResponse);
                }

                @Override
                public void onFailure(Exception e) {
                    log.error("Failed to delete ML Agent " + agentId, e);
                    wrappedListener.onFailure(e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to delete ml agent " + agentId, e);
            actionListener.onFailure(e);
        }
    }
}
