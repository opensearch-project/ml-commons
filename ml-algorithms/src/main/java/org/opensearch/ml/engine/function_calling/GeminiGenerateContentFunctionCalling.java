/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_NAME;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.removeJsonPath;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.INTERACTION_TEMPLATE_TOOL_RESPONSE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class GeminiGenerateContentFunctionCalling implements FunctionCalling {
    public static final String CALL_PATH = "$.candidates[0].content.parts[*].functionCall";
    public static final String NAME = "name";
    public static final String INPUT = "args";
    public static final String ID_PATH = "name";
    public static final String TOOL_ERROR = "tool_error";
    public static final String GEMINI_TOOL_TEMPLATE =
        "{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"parameters\":${tool.attributes.input_schema_cleaned}}";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
        }
        params.put(LLM_RESPONSE_FILTER, "$.candidates[0].content.parts[0].text");

        params.put(TOOL_TEMPLATE, GEMINI_TOOL_TEMPLATE);
        // Add a custom processor to clean input_schema before substitution
        params.put("gemini.schema.cleaner", "true");
        params.put(TOOL_CALLS_PATH, "$.candidates[0].content.parts[*].functionCall");
        params.put(TOOL_CALLS_TOOL_NAME, "name");
        params.put(TOOL_CALLS_TOOL_INPUT, "args");
        params.put(TOOL_CALL_ID_PATH, "name");
        params
            .put(
                "tool_configs",
                ", \"tools\": [{\"functionDeclarations\": [${parameters._tools:-}]}], \"toolConfig\": {\"functionCallingConfig\": {\"mode\": \"AUTO\"}}"
            );

        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$.candidates[0].content");
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"user\",\"parts\":[{\"functionResponse\":{\"name\":\"${_interactions.tool_call_id}\",\"response\":${_interactions.tool_response}}}]}"
            );

        params.put(CHAT_HISTORY_QUESTION_TEMPLATE, "{\"role\":\"user\",\"parts\":[{\"text\":\"${_chat_history.message.question}\"}]}");
        params.put(CHAT_HISTORY_RESPONSE_TEMPLATE, "{\"role\":\"model\",\"parts\":[{\"text\":\"${_chat_history.message.response}\"}]}");

        params.put(LLM_FINISH_REASON_PATH, "$.candidates[0].finishReason");
        params.put(LLM_FINISH_REASON_TOOL_USE, "STOP");
    }

    @Override
    public List<Map<String, String>> handle(ModelTensorOutput tmpModelTensorOutput, Map<String, String> parameters) {
        List<Map<String, String>> output = new ArrayList<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String llmResponseExcludePath = parameters.get(LLM_RESPONSE_EXCLUDE_PATH);
        if (llmResponseExcludePath != null) {
            dataAsMap = removeJsonPath(dataAsMap, llmResponseExcludePath, true);
        }
        // For Gemini, finishReason is "STOP" for both normal and tool calls
        // So we check for functionCall existence instead
        List functionCalls;
        try {
            functionCalls = JsonPath.read(dataAsMap, CALL_PATH);
        } catch (PathNotFoundException e) {
            return output;
        }
        if (CollectionUtils.isEmpty(functionCalls)) {
            return output;
        }
        for (Object call : functionCalls) {
            String toolName = JsonPath.read(call, NAME);
            String toolInput = StringUtils.toJson(JsonPath.read(call, INPUT));
            String toolCallId = JsonPath.read(call, ID_PATH);
            output.add(Map.of("tool_name", toolName, "tool_input", toolInput, "tool_call_id", toolCallId));
        }
        return output;
    }

    @Override
    public List<LLMMessage> supply(List<Map<String, Object>> toolResults) {
        GeminiMessage toolMessage = new GeminiMessage();
        for (Map toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(TOOL_CALL_ID);
            if (toolUseId == null) {
                continue;
            }
            Map<String, Object> functionResponse = Map.of("name", toolUseId, "response", toolResult.get(TOOL_RESULT));
            toolMessage.getParts().add(Map.of("functionResponse", functionResponse));
            if (toolResult.containsKey(TOOL_ERROR)) {
                // Gemini may not need status field, but keep for consistency
                log.debug("Tool error detected for function: {}", toolUseId);
            }
        }

        return List.of(toolMessage);
    }

    @Override
    public Map<String, ?> filterToFirstToolCall(Map<String, ?> dataAsMap, Map<String, String> parameters) {
        try {
            List<Object> partsList = JsonPath.read(dataAsMap, "$.candidates[0].content.parts");
            if (partsList == null || partsList.size() <= 1) {
                return dataAsMap;
            }

            // Keep only text parts and first functionCall
            List<Object> filteredParts = new ArrayList<>();
            List<String> allToolNames = new ArrayList<>();
            String selectedToolName = null;
            boolean foundFirstFunctionCall = false;

            for (Object item : partsList) {
                if (item instanceof Map && ((Map<?, ?>) item).containsKey("functionCall")) {
                    Map<?, ?> functionCallMap = (Map<?, ?>) ((Map<?, ?>) item).get("functionCall");
                    String toolName = functionCallMap != null ? String.valueOf(functionCallMap.get("name")) : "unknown";
                    allToolNames.add(toolName);

                    if (!foundFirstFunctionCall) {
                        filteredParts.add(item);
                        selectedToolName = toolName;
                        foundFirstFunctionCall = true;
                    }
                } else {
                    filteredParts.add(item);
                }
            }

            if (!foundFirstFunctionCall) {
                return dataAsMap;
            }

            if (allToolNames.size() > 1) {
                log.info("LLM suggested {} tool(s): {}. Selected first tool: {}", allToolNames.size(), allToolNames, selectedToolName);
            }

            // Create mutable copy using JSON serialization for efficiency
            Map<String, Object> mutableCopy = gson.fromJson(StringUtils.toJson(dataAsMap), Map.class);
            DocumentContext context = JsonPath.parse(mutableCopy);
            context.set("$.candidates[0].content.parts", filteredParts);
            return context.json();
        } catch (Exception e) {
            log.error("Failed to filter out to only first tool call", e);
            return dataAsMap;
        }
    }

    @Override
    public String formatAGUIToolCalls(String toolCallsJson) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
