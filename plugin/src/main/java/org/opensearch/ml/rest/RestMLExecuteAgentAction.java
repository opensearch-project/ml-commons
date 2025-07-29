/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.rest.RestRequest.Method.POST;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.opensearch.client.node.NodeClient;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLExecuteAgentAction;
import org.opensearch.ml.common.transport.agent.MLExecuteAgentRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

public class RestMLExecuteAgentAction extends BaseRestHandler {

    private MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLExecuteAgentAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public List<Route> routes() {
        return List.of(new Route(POST, "/_plugins/_ml/agents/{agent_id}/_execute"));
    }

    @Override
    public String getName() {
        return "ml_execute_agent_action";
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        String agentId = request.param("agent_id");
        XContentParser parser = request.contentParser();
        Map<String, Object> params = parser.map();
        Map<String, String> parameters = (Map<String, String>) params.get("parameters");

        MLExecuteAgentRequest mlExecuteAgentRequest = new MLExecuteAgentRequest(agentId, request.method().name(), parameters);

        return channel -> client.execute(MLExecuteAgentAction.INSTANCE, mlExecuteAgentRequest, new RestToXContentListener<>(channel));
    }
}
