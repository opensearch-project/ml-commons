/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.agents;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.utils.MLNodeUtils.createXContentParserFromRegistry;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
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
import org.opensearch.index.IndexNotFoundException;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.ml.common.transport.agent.MLAgentGetResponse;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class GetAgentTransportAction extends HandledTransportAction<ActionRequest, MLAgentGetResponse> {

    Client client;
    NamedXContentRegistry xContentRegistry;

    ClusterService clusterService;

    @Inject
    public GetAgentTransportAction(
        TransportService transportService,
        ActionFilters actionFilters,
        Client client,
        ClusterService clusterService,
        NamedXContentRegistry xContentRegistry
    ) {
        super(MLAgentGetAction.NAME, transportService, actionFilters, MLAgentGetRequest::new);
        this.client = client;
        this.xContentRegistry = xContentRegistry;
        this.clusterService = clusterService;
    }

    @Override
    protected void doExecute(Task task, ActionRequest request, ActionListener<MLAgentGetResponse> actionListener) {
        MLAgentGetRequest mlAgentGetRequest = MLAgentGetRequest.fromActionRequest(request);
        String agentId = mlAgentGetRequest.getAgentId();
        GetRequest getRequest = new GetRequest(ML_AGENT_INDEX).id(agentId);
        boolean isSuperAdmin = isSuperAdminUserWrapper(clusterService, client);

        try (ThreadContext.StoredContext context = client.threadPool().getThreadContext().stashContext()) {
            client.get(getRequest, ActionListener.runBefore(ActionListener.wrap(r -> {
                log.debug("Completed Get Agent Request, id:{}", agentId);

                if (r != null && r.isExists()) {
                    try (XContentParser parser = createXContentParserFromRegistry(xContentRegistry, r.getSourceAsBytesRef())) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
                        MLAgent mlAgent = MLAgent.parse(parser);
                        if (mlAgent.getIsHidden() && !isSuperAdmin) {
                            actionListener
                                .onFailure(
                                    new OpenSearchStatusException(
                                        "User doesn't have privilege to perform this operation on this agent",
                                        RestStatus.FORBIDDEN
                                    )
                                );
                        } else {
                            actionListener.onResponse(MLAgentGetResponse.builder().mlAgent(mlAgent).build());
                        }
                    } catch (Exception e) {
                        log.error("Failed to parse ml agent" + r.getId(), e);
                        actionListener.onFailure(e);
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
            }, e -> {
                if (e instanceof IndexNotFoundException) {
                    log.error("Failed to get agent index", e);
                    actionListener.onFailure(new OpenSearchStatusException("Failed to get agent index", RestStatus.NOT_FOUND));
                } else {
                    log.error("Failed to get ML agent " + agentId, e);
                    actionListener.onFailure(e);
                }
            }), context::restore));
        } catch (Exception e) {
            log.error("Failed to get ML agent " + agentId, e);
            actionListener.onFailure(e);
        }
    }

    @VisibleForTesting
    boolean isSuperAdminUserWrapper(ClusterService clusterService, Client client) {
        return RestActionUtils.isSuperAdminUser(clusterService, client);
    }
}
