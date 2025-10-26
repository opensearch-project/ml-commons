/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.output.model.ModelTensorOutput;

/**
 * A general LLM function calling interface.
 */
public interface FunctionCalling {

    /**
     * Configure all parameters related to function calling.
     * @param params the parameters used to configure a request to LLM
     */
    void configure(Map<String, String> params);

    /**
     * Handle the response from LLM to get the function calling context.
     * @param modelTensorOutput the response from LLM
     * @param parameters some parameters
     * @return a list of tools with something like name, input, etc.
     */
    List<Map<String, String>> handle(ModelTensorOutput modelTensorOutput, Map<String, String> parameters);

    /**
     * According to results of tools to render a LLMMessage provided to LLM
     * @param toolResults results from tools
     * @return a LLMMessage containing tool results.
     */
    List<LLMMessage> supply(List<Map<String, Object>> toolResults);

    /**
     * Format AG-UI tool calls into an assistant message in LLM-specific format.
     * This is used by AG-UI to convert tool calls from AG-UI format (OpenAI-compatible)
     * to the format expected by the specific LLM (OpenAI, Bedrock, etc.).
     *
     * @param toolCallsJson JSON string containing array of tool calls from AG-UI.
     *                      Each tool call contains:
     *                      - "id": tool call identifier
     *                      - "type": typically "function"
     *                      - "function": object with "name" and "arguments"
     * @return JSON string representing the assistant message with tool calls in LLM-specific format
     */
    String formatAGUIToolCalls(String toolCallsJson);
}
