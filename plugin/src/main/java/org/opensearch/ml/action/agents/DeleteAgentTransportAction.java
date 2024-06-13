/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;
import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.delete.DeleteResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class DeleteAgentTransportAction extends HandledTransportAction<ActionRequest, DeleteResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;

    @Inject
    public DeleteAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        NamedXContentRegistry xContentRegistry,
        ClusterService clusterService
    ) {
        super(MLAgentDeleteAction.NAME, transportService, actionFilters, MLAgentDeleteRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<DeleteResponse> actionListener) {
        MLAgentDeleteRequest mlAgentDeleteRequest = MLAgentDeleteRequest.fromActionRequest(request);
        String agentId = mlAgentDeleteRequest.getAgentId();
        GetRequest getRequest = new GetRequest(ML_AGENT_INDEX).id(agentId);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.wrap(getResponse -> {
                if (getResponse.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, getResponse.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLAgent mlAgent = MLAgent.parse(parser);
                        if (mlAgent.getIsHidden() && !isSuperAdmin) {
                            actionListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "User doesn't have privilege to perform this operation on this hidden agent",
                                        RestStatus.FORBIDDEN
                                    )
                                );
                        } else {
                            // If the agent is not hidden or if the user is a super admin, proceed with deletion
                            DeleteRequest deleteRequest = new DeleteRequest(ML_AGENT_INDEX, agentId).setRefreshPolicy(IMMEDIATE);
                            client.delete(deleteRequest, ActionListener.wrap(deleteResponse -> {
                                log.debug("Completed Delete Agent Request, agent id:{} deleted", agentId);
                                actionListener.onResponse(deleteResponse);
                            }, deleteException -> {
                                log.error("Failed to delete ML Agent " + agentId, deleteException);
                                actionListener.onFailure(deleteException);
                            }));
                        }
                    } catch (Exception parseException) {
                        log.error("Failed to parse ml agent " + getResponse.getId(), parseException);
                        actionListener.onFailure(parseException);
                    }
                } else {
                    actionListener
                        .onFailure(
                            new OpenSearchStatusException(
                                "Failed to find agent with the provided agent id: " + agentId,
                                RestStatus.NOT_FOUND
                            )
                        );
                }
            }, getException -> {
                log.error("Failed to get ml agent " + agentId, getException);
                actionListener.onFailure(getException);
            }));
        } catch (Exception e) {
            log.error("Failed to delete ml agent " + agentId, e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
