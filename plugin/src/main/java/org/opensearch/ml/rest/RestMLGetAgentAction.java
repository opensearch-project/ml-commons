/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.getParameterId;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.agent.MLAgentGetAction;
import org.opensearch.ml.common.transport.agent.MLAgentGetRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLGetAgentAction extends BaseRestHandler {
    private static final String ML_GET_Agent_ACTION = "ml_get_agent_action";

    /**
     * Constructor
     */
    public RestMLGetAgentAction() {}

    @Override
    public String getName() {
        return ML_GET_Agent_ACTION;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(RestRequest.Method.GET, String.format(Locale.ROOT, "%s/agents/{%s}", ML_BASE_URI, PARAMETER_AGENT_ID)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLAgentGetRequest mlAgentGetRequest = getRequest(request);
        return channel -> client.execute(MLAgentGetAction.INSTANCE, mlAgentGetRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLAgentGetRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLAgentGetRequest
     */
    // VisibleForTesting
    MLAgentGetRequest getRequest(RestRequest request) throws IOException {
        String agentId = getParameterId(request, PARAMETER_AGENT_ID);

        return new MLAgentGetRequest(agentId);
    }
}
