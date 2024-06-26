/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.RestActionUtils.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteAction;
import org.opensearch.ml.common.transport.agent.MLAgentDeleteRequest;
import org.opensearch.ml.settings.MLFeatureEnabledSetting;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;

import com.google.common.collect.ImmutableList;

/**
 * This class consists of the REST handler to delete ML Agent.
 */
public class RestMLDeleteAgentAction extends BaseRestHandler {
    private static final String ML_DELETE_AGENT_ACTION = "ml_delete_agent_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    public RestMLDeleteAgentAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_DELETE_AGENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.DELETE, String.format(Locale.ROOT, "%s/agents/{%s}", ML_BASE_URI, PARAMETER_AGENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String agentId = request.param(PARAMETER_AGENT_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        MLAgentDeleteRequest mlAgentDeleteRequest = new MLAgentDeleteRequest(agentId, tenantId);
        return channel -> client.execute(MLAgentDeleteAction.INSTANCE, mlAgentDeleteRequest, new RestToXContentListener<>(channel));
    }

}
