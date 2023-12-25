package org.opensearch.ml.engine.algorithms.agent;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLAgent;

/**
 * Agent executor interface definition. Agent executor will be used by {@link MLAgentExecutor} to invoke agents.
 */
public interface MLAgentRunner {

    /**
     * Function interface to execute agent.
     * @param mlAgent
     * @param params
     * @param listener
     */
    void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener);
}
