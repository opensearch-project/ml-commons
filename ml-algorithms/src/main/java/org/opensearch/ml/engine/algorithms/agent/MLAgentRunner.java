/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.transport.TransportChannel;

/**
 * Agent executor interface definition. Agent executor will be used by {@link MLAgentExecutor} to invoke agents.
 */
public interface MLAgentRunner {

    /**
     * Function interface to execute agent.
     * @param mlAgent
     * @param params
     * @param listener
     * @param channel
     */
    void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel);
}
