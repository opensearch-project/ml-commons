/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

/**
 * This class consists of the REST handler to delete ML Agent.
 */
public class RestMLDeleteAgentAction extends BaseRestHandler {
    private static final String ML_DELETE_AGENT_ACTION = "ml_delete_agent_action";

    public void RestMLDeleteAgentAction() {}

    @Override
    public String getName() {
        return ML_DELETE_AGENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/agents/{%s}", ML_BASE_URI, PARAMETER_AGENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String agentId = request.param(PARAMETER_AGENT_ID);

        MLAgentDeleteRequest mlAgentDeleteRequest = new MLAgentDeleteRequest(agentId);
        return channel -> client.execute(MLAgentDeleteAction.INSTANCE, mlAgentDeleteRequest, new RestToXContentListener<>(channel));
    }

}
