/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;
import static org.opensearch.ml.utils.TenantAwareHelper.getTenantID;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentAction;
import org.opensearch.ml.common.transport.agent.MLRegisterAgentRequest;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.action.RestToXContentListener;
import org.opensearch.transport.client.node.NodeClient;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

public class RestMLRegisterAgentAction extends BaseRestHandler {
    private static final String ML_REGISTER_AGENT_ACTION = "ml_register_agent_action";
    private final MLFeatureEnabledSetting mlFeatureEnabledSetting;

    /**
     * Constructor
     */
    public RestMLRegisterAgentAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_REGISTER_AGENT_ACTION;
    }

    @Override
    public List<Route> routes() {
        return ImmutableList.of(new Route(RestRequest.Method.POST, String.format(Locale.ROOT, "%s/agents/_register", ML_BASE_URI)));
    }

    @Override
    public RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        MLRegisterAgentRequest registerAgentRequest = getRequest(request);
        return channel -> client.execute(MLRegisterAgentAction.INSTANCE, registerAgentRequest, new RestToXContentListener<>(channel));
    }

    /**
     * Creates a MLTrainingTaskRequest from a RestRequest
     *
     * @param request RestRequest
     * @return MLTrainingTaskRequest
     */
    @VisibleForTesting
    MLRegisterAgentRequest getRequest(RestRequest request) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }
        String tenantId = getTenantID(mlFeatureEnabledSetting.isMultiTenancyEnabled(), request);
        XContentParser parser = request.contentParser();
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);
        MLAgent mlAgent = MLAgent.parseFromUserInput(parser).toBuilder().tenantId(tenantId).build();

        // Check if simplified agent registration is being used but not enabled
        if (mlAgent.getModel() != null && !mlFeatureEnabledSetting.isSimplifiedAgentRegistrationEnabled()) {
            throw new IllegalArgumentException(
                "Simplified agent registration is not enabled. To enable, please update the setting plugins.ml_commons.simplified_agent_registration_enabled"
            );
        }

        return new MLRegisterAgentRequest(mlAgent);
    }
}
