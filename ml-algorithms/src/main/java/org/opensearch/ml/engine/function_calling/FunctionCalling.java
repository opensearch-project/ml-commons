/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.agent.TokenUsage;
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
     * Filters the dataAsMap to keep only the first tool call for interaction history.
     * This prevents the model from expecting results for multiple tool calls when only the first one is executed.
     * Default implementation returns the original dataAsMap unchanged.
     *
     * @param dataAsMap the original response data map
     * @param parameters configuration parameters
     * @return filtered data map containing only the first tool call, or original if no filtering needed
     */
    default Map<String, ?> filterToFirstToolCall(Map<String, ?> dataAsMap, Map<String, String> parameters) {
        return dataAsMap;
    }

    /**
     * Extracts token usage information from the LLM response.
     * Each implementation knows its own response format and field names.
     *
     * @param llmResponseDataAsMap the full LLM response data map
     * @return TokenUsage object with token counts, or null if extraction fails or is not supported
     */
    default TokenUsage extractTokenUsage(Map<String, ?> llmResponseDataAsMap) {
        return null; // Default: no token tracking
    }

    /**
     * Indicates whether this provider supports strict schema enforcement.
     * When true, the provider guarantees that tool outputs conform to the declared schema.
     * When false, the framework will apply additional validation before tool execution.
     *
     * @return true if provider supports strict schema enforcement (e.g., OpenAI with strict=true),
     *         false if framework-level validation is needed (e.g., Bedrock, Gemini)
     */
    default boolean supportsStrictSchema() {
        return false; // Default: assume no strict schema support
    }

    /**
     * Whether this provider can force the model to call one named tool (see {@link #forcedToolConfigs}).
     */
    default boolean supportsForcedToolCall() {
        return false;
    }

    /**
     * Build the {@code tool_configs} request fragment that declares a single tool and forces the model
     * to call it. The fragment is spliced into the connector request body through the
     * {@code ${parameters.tool_configs}} placeholder, so it starts with a comma like {@link #configure} does.
     *
     * @param toolName the tool name the model must call
     * @param toolDescription the tool description
     * @param inputSchema the tool's JSON Schema as a map
     * @return the request fragment
     * @throws UnsupportedOperationException if the provider cannot force a tool call
     */
    default String forcedToolConfigs(String toolName, String toolDescription, Map<String, Object> inputSchema) {
        throw new UnsupportedOperationException("This LLM interface does not support forced tool calls");
    }

    /**
     * Extract the input of the named tool call from a response to a {@link #forcedToolConfigs} request.
     * Unlike {@link #handle}, this does not depend on the finish reason, which some providers report as
     * a normal stop when the tool choice is forced.
     *
     * @param modelTensorOutput the response from LLM
     * @param toolName the forced tool's name
     * @return the tool input as a JSON string, or null if the response carries no call to that tool
     * @throws UnsupportedOperationException if the provider cannot force a tool call
     */
    default String extractForcedToolInput(ModelTensorOutput modelTensorOutput, String toolName) {
        throw new UnsupportedOperationException("This LLM interface does not support forced tool calls");
    }
}
