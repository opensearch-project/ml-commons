/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.rest;

import static org.opensearch.ml.common.CommonValue.ML_AGENT_INDEX;
import static org.opensearch.ml.plugin.MachineLearningPlugin.ML_BASE_URI;

import java.util.List;

import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.transport.agent.MLSearchAgentAction;

/**
 * This class consists of the REST handler to search ML Agents.
 */
public class RestMLSearchAgentAction extends AbstractMLSearchAction<MLAgent> {
    private static final String ML_SEARCH_AGENT_ACTION = "ml_search_agent_action";
    private static final String SEARCH_AGENT_PATH = ML_BASE_URI + "/agents/_search";

    public RestMLSearchAgentAction() {
        super(List.of(SEARCH_AGENT_PATH), ML_AGENT_INDEX, MLAgent.class, MLSearchAgentAction.INSTANCE);
    }

    @Override
    public String getName() {
        return ML_SEARCH_AGENT_ACTION;
    }
}
