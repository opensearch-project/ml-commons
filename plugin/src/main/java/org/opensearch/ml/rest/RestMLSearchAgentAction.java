/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;
import static org.opensearch.ml.utils.MLExceptionUtils.AGENT_FRAMEWORK_DISABLED_ERR_MSG;

import java.io.IOException;

import org.opensearch.client.node.NodeClient;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.agent.MLSearchAgentAction;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableList;
import org.opensearch.rest.RestRequest;

/**
 * This class consists of the REST handler to search ML Agents.
 */
public class RestMLSearchAgentAction extends AbstractMLSearchAction<MLAgent> {
    private static final String ML_SEARCH_AGENT_ACTION = "ml_search_agent_action";
    private static final String SEARCH_AGENT_PATH = ML_BASE_URI + "/agents/_search";

    public RestMLSearchAgentAction(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        super(ImmutableList.of(SEARCH_AGENT_PATH), ML_AGENT_INDEX, MLAgent.class, MLSearchAgentAction.INSTANCE, mlFeatureEnabledSetting);
        this.mlFeatureEnabledSetting = mlFeatureEnabledSetting;
    }

    @Override
    public String getName() {
        return ML_SEARCH_AGENT_ACTION;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
        if (!mlFeatureEnabledSetting.isAgentFrameworkEnabled()) {
            throw new IllegalStateException(AGENT_FRAMEWORK_DISABLED_ERR_MSG);
        }

        return super.prepareRequest(request, client);
    }
}
