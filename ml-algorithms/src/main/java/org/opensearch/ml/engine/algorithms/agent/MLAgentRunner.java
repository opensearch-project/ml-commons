/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import java.util.List;
import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.memory.Memory;
import org.opensearch.transport.TransportChannel;

/**
 * Agent executor interface definition. Agent executor will be used by {@link MLAgentExecutor} to invoke agents.
 */
public interface MLAgentRunner {

    /**
     * Function interface to execute agent (non-streaming)
     * @param mlAgent
     * @param params
     * @param listener
     */
    default void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener) {
        run(mlAgent, params, listener, null);
    }

    /**
     * Function interface to execute agent (streaming)
     * @param mlAgent
     * @param params
     * @param listener
     * @param channel
     */
    void run(MLAgent mlAgent, Map<String, String> params, ActionListener<Object> listener, TransportChannel channel);

    /**
     * Function interface to execute agent with an executor-provided memory instance.
     * For unified interface, the executor skips parent interaction creation and passes its
     * memory instance so the runner reuses it instead of creating its own.
     * @param mlAgent
     * @param params
     * @param listener
     * @param channel
     * @param memory executor-provided memory instance (may be null)
     */
    default void run(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        TransportChannel channel,
        Memory memory
    ) {
        run(mlAgent, params, listener, channel);
    }

    /**
     * Run V2 agent with pure message-centric flow.
     * V2 agents work exclusively with List<Message>.
     * Executor handles memory operations (fetch history, save messages).
     * Runner receives complete conversation and executes agent logic.
     *
     * @param mlAgent the agent configuration
     * @param params execution parameters (minimal - no body, no _chat_history, no question)
     * @param listener response listener
     * @param channel transport channel
     * @param memory agentic memory instance (for saving assistant response)
     * @param fullConversation complete conversation history including new user input
     */
    default void runV2(
        MLAgent mlAgent,
        Map<String, String> params,
        ActionListener<Object> listener,
        TransportChannel channel,
        Memory memory,
        List<Message> fullConversation
    ) {
        throw new UnsupportedOperationException("runV2 is only supported for V2 agents (CONVERSATIONAL_V2)");
    }
}
