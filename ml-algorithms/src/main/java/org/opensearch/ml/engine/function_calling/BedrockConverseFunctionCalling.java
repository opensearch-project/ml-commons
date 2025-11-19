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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;

import com.google.gson.Gson;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class BedrockConverseFunctionCalling implements FunctionCalling {
    public static final String FINISH_REASON_PATH = "$.stopReason";
    public static final String FINISH_REASON = "tool_use";
    public static final String CALL_PATH = "$.output.message.content[*].toolUse";
    public static final String NAME = "name";
    public static final String INPUT = "input";
    public static final String ID_PATH = "toolUseId";
    public static final String TOOL_ERROR = "tool_error";
    public static final String BEDROCK_CONVERSE_TOOL_TEMPLATE =
        "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
        }
        params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");

        params.put(TOOL_TEMPLATE, BEDROCK_CONVERSE_TOOL_TEMPLATE);
        params.put(TOOL_CALLS_PATH, "$.output.message.content[*].toolUse");
        params.put(TOOL_CALLS_TOOL_NAME, "name");
        params.put(TOOL_CALLS_TOOL_INPUT, "input");
        params.put(TOOL_CALL_ID_PATH, "toolUseId");
        params.put("tool_configs", ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}");

        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$.output.message");
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"user\",\"content\":[{\"toolResult\":{\"toolUseId\":\"${_interactions.tool_call_id}\",\"content\":[{\"text\":\"${_interactions.tool_response}\"}]}}]}"
            );

        params.put(CHAT_HISTORY_QUESTION_TEMPLATE, "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}");
        params
            .put(CHAT_HISTORY_RESPONSE_TEMPLATE, "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}");

        params.put(LLM_FINISH_REASON_PATH, "$.stopReason");
        params.put(LLM_FINISH_REASON_TOOL_USE, "tool_use");
    }

    @Override
    public List<Map<String, String>> handle(ModelTensorOutput tmpModelTensorOutput, Map<String, String> parameters) {
        List<Map<String, String>> output = new ArrayList<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String llmResponseExcludePath = parameters.get(LLM_RESPONSE_EXCLUDE_PATH);
        if (llmResponseExcludePath != null) {
            dataAsMap = removeJsonPath(dataAsMap, llmResponseExcludePath, true);
        }
        String llmFinishReason = JsonPath.read(dataAsMap, FINISH_REASON_PATH);
        if (!llmFinishReason.contentEquals(FINISH_REASON)) {
            return output;
        }
        List toolCalls = JsonPath.read(dataAsMap, CALL_PATH);
        if (CollectionUtils.isEmpty(toolCalls)) {
            return output;
        }
        for (Object call : toolCalls) {
            String toolName = JsonPath.read(call, NAME);
            String toolInput = StringUtils.toJson(JsonPath.read(call, INPUT));
            String toolCallId = JsonPath.read(call, ID_PATH);
            output.add(Map.of("tool_name", toolName, "tool_input", toolInput, "tool_call_id", toolCallId));
        }
        return output;
    }

    @Override
    public List<LLMMessage> supply(List<Map<String, Object>> toolResults) {
        BedrockMessage toolMessage = new BedrockMessage();
        for (Map toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(TOOL_CALL_ID);
            if (toolUseId == null) {
                continue;
            }
            ToolResult result = new ToolResult();
            result.setToolUseId(toolUseId);
            result.getContent().add(toolResult.get(TOOL_RESULT));
            if (toolResult.containsKey(TOOL_ERROR)) {
                result.setStatus("error");
            }
            toolMessage.getContent().add(Map.of("toolResult", result));
        }

        return List.of(toolMessage);
    }

    @Override
    public Map<String, ?> filterToFirstToolCall(Map<String, ?> dataAsMap, Map<String, String> parameters) {
        try {
            List<Object> contentList = JsonPath.read(dataAsMap, "$.output.message.content");
            if (contentList == null || contentList.size() <= 1) {
                return dataAsMap;
            }

            // Keep only text and first toolUse
            List<Object> filteredContent = new ArrayList<>();
            List<String> allToolNames = new ArrayList<>();
            String selectedToolName = null;
            boolean foundFirstToolUse = false;

            for (Object item : contentList) {
                if (item instanceof Map && ((Map<?, ?>) item).containsKey("toolUse")) {
                    Map<?, ?> toolUseMap = (Map<?, ?>) ((Map<?, ?>) item).get("toolUse");
                    String toolName = toolUseMap != null ? String.valueOf(toolUseMap.get("name")) : "unknown";
                    allToolNames.add(toolName);

                    if (!foundFirstToolUse) {
                        filteredContent.add(item);
                        selectedToolName = toolName;
                        foundFirstToolUse = true;
                    }
                } else {
                    filteredContent.add(item);
                }
            }

            if (!foundFirstToolUse) {
                return dataAsMap;
            }

            if (allToolNames.size() > 1) {
                log.info("LLM suggested {} tool(s): {}. Selected first tool: {}", allToolNames.size(), allToolNames, selectedToolName);
            }

            // Create mutable copy using JSON serialization for efficiency
            Map<String, Object> mutableCopy = gson.fromJson(StringUtils.toJson(dataAsMap), Map.class);
            DocumentContext context = JsonPath.parse(mutableCopy);
            context.set("$.output.message.content", filteredContent);
            return context.json();
        } catch (Exception e) {
            log.error("Failed to filter out to only first tool call", e);
            return dataAsMap;
        }
    }

    @Override
    public String formatAGUIToolCalls(String toolCallsJson) {
        BedrockMessage assistantMessage = new BedrockMessage("assistant");
        Gson gson = new Gson();

        try {
            List toolCalls = gson.fromJson(toolCallsJson, List.class);
            for (Object toolCallObj : toolCalls) {
                Map<String, Object> toolCall = (Map<String, Object>) toolCallObj;
                Map<String, Object> toolUse = new HashMap<>();
                toolUse.put("toolUseId", toolCall.get("id"));

                Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                if (function != null) {
                    toolUse.put("name", function.get("name"));

                    String argumentsJson = (String) function.get("arguments");
                    try {
                        Object argumentsObj = gson.fromJson(argumentsJson, Object.class);
                        toolUse.put("input", argumentsObj);
                    } catch (Exception e) {
                        toolUse.put("input", Map.of());
                    }
                }

                assistantMessage.getContent().add(Map.of("toolUse", toolUse));
            }
        } catch (Exception e) {
            return "{\"role\":\"assistant\",\"content\":[]}";
        }

        return assistantMessage.getResponse();
    }

    @Data
    public static class ToolResult {
        private String toolUseId;
        private List<Object> content = new ArrayList<>();
        private String status;
    }
}
