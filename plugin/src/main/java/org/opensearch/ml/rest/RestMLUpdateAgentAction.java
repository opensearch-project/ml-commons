/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.RestActionUtils.PARAMETER_AGENT_ID;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateAction;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateInput;
import org.opensearch.ml.common.transport.agent.MLAgentUpdateRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLUpdateAgentAction extends BaseRestHandler {

    private static final String ML_UPDATE_AGENT_ACTION = "ml_update_agent_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLUpdateAgentAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_UPDATE_AGENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList
            .of(new Route(RestRequest.Method.PUT, String.format(Locale.ROOT, "%s/agents/{%s}", ML_BASE_URI, PARAMETER_AGENT_ID)));
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLAgentUpdateRequest mlAgentUpdateRequest = getRequest(request);
        return channel -> client.execute(MLAgentUpdateAction.INSTANCE, mlAgentUpdateRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLAgentUpdateRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLAgentUpdateRequest
     */
    @VisibleForTesting
    MLAgentUpdateRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }

        String agentId = request.param(PARAMETER_AGENT_ID);
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);

        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLAgentUpdateInput mlAgentUpdateInput = MLAgentUpdateInput.parse(parser).toBuilder().agentId(agentId).tenantId(tenantId).build();

        return new MLAgentUpdateRequest(mlAgentUpdateInput);
    }
}
