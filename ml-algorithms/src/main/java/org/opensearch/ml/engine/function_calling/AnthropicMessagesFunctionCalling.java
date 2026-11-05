/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOKEN_USAGE_PATH;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.algorithms.agent.AgentUtils;

import lombok.extern.log4j.Log4j2;

/**
 * Function calling for the Anthropic Messages API.
 *
 * <p>Tools are declared as {@code {"name", "description", "input_schema"}}. A response with
 * {@code stop_reason: "tool_use"} carries {@code tool_use} content blocks ({@code id}, {@code name},
 * {@code input}); each tool result goes back as a user message with a {@code tool_result} block that
 * references the call's {@code tool_use_id}.
 */
@Log4j2
public class AnthropicMessagesFunctionCalling implements FunctionCalling {
    public static final String FINISH_REASON_PATH = "$.stop_reason";
    public static final String FINISH_REASON = "tool_use";
    public static final String CALL_PATH = "$.content[?(@.type == 'tool_use')]";
    public static final String NAME = "name";
    public static final String INPUT = "input";
    public static final String ID_PATH = "id";
    public static final String TOOL_ERROR = "tool_error";
    public static final String ANTHROPIC_TOOL_TEMPLATE =
        "{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"input_schema\":${tool.attributes.input_schema}}";

    // The response is the assistant message plus response-only fields, which a request message must not carry
    private static final String RESPONSE_ONLY_FIELDS =
        "[\"$.id\",\"$.type\",\"$.model\",\"$.stop_reason\",\"$.stop_sequence\",\"$.usage\"]";

    private static final String TOOL_USE_TYPE = "tool_use";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
        }
        params.put(LLM_RESPONSE_FILTER, "$.content[0].text");

        params.put(TOOL_TEMPLATE, ANTHROPIC_TOOL_TEMPLATE);
        params.put(TOOL_CALLS_PATH, CALL_PATH);
        params.put(TOOL_CALLS_TOOL_NAME, NAME);
        params.put(TOOL_CALLS_TOOL_INPUT, INPUT);
        params.put(TOOL_CALL_ID_PATH, ID_PATH);
        params.put("tool_configs", ", \"tools\": [${parameters._tools:-}]");

        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$");
        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH, RESPONSE_ONLY_FIELDS);
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"${_interactions.tool_call_id}\",\"content\":\"${_interactions.tool_response}\"}]}"
            );

        params
            .put(
                CHAT_HISTORY_QUESTION_TEMPLATE,
                "{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"${_chat_history.message.question}\"}]}"
            );
        params
            .put(
                CHAT_HISTORY_RESPONSE_TEMPLATE,
                "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"${_chat_history.message.response}\"}]}"
            );

        params.put(LLM_FINISH_REASON_PATH, FINISH_REASON_PATH);
        params.put(LLM_FINISH_REASON_TOOL_USE, FINISH_REASON);

        // Token usage tracking paths
        params.put(TOKEN_USAGE_PATH, "$.usage");
    }

    @Override
    public List<Map<String, String>> handle(ModelTensorOutput tmpModelTensorOutput, Map<String, String> parameters) {
        List<Map<String, String>> output = new ArrayList<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        String llmResponseExcludePath = parameters.get(LLM_RESPONSE_EXCLUDE_PATH);
        if (llmResponseExcludePath != null) {
            dataAsMap = removeJsonPath(dataAsMap, llmResponseExcludePath, true);
        }
        if (!FINISH_REASON.equals(dataAsMap.get("stop_reason"))) {
            return output;
        }
        for (Map<?, ?> toolUse : toolUseBlocks(dataAsMap)) {
            Object input = toolUse.get(INPUT);
            output
                .add(
                    Map
                        .of(
                            "tool_name",
                            String.valueOf(toolUse.get(NAME)),
                            "tool_input",
                            input != null ? StringUtils.toJson(input) : "{}",
                            "tool_call_id",
                            String.valueOf(toolUse.get(ID_PATH))
                        )
                );
        }
        return output;
    }

    /**
     * Returns the {@code tool_use} blocks of a response's content, in order.
     */
    private static List<Map<?, ?>> toolUseBlocks(Map<String, ?> dataAsMap) {
        List<Map<?, ?>> toolUses = new ArrayList<>();
        if (dataAsMap != null && dataAsMap.get("content") instanceof List<?> content) {
            for (Object item : content) {
                if (isToolUse(item)) {
                    toolUses.add((Map<?, ?>) item);
                }
            }
        }
        return toolUses;
    }

    private static boolean isToolUse(Object contentBlock) {
        return contentBlock instanceof Map && TOOL_USE_TYPE.equals(((Map<?, ?>) contentBlock).get("type"));
    }

    /**
     * Returns one user message per tool result. Consecutive tool results are merged into a single user
     * message when the conversation is rendered, and Anthropic also combines consecutive user turns.
     */
    @Override
    public List<LLMMessage> supply(List<Map<String, Object>> toolResults) {
        List<LLMMessage> messages = new ArrayList<>();
        for (Map<String, Object> toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(TOOL_CALL_ID);
            if (toolUseId == null) {
                continue;
            }
            messages.add(new AnthropicMessage("user", List.of(toolResultBlock(toolUseId, toolResult))));
        }
        return messages;
    }

    private static Map<String, Object> toolResultBlock(String toolUseId, Map<String, Object> toolResult) {
        Object result = toolResult.get(TOOL_RESULT);
        boolean isError = toolResult.containsKey(TOOL_ERROR);
        Object content = result;
        if (result instanceof Map<?, ?> resultMap && resultMap.containsKey("text")) {
            content = resultMap.get("text");
        } else if (result instanceof Map<?, ?> resultMap && resultMap.containsKey("error")) {
            content = resultMap.get("error");
            isError = true;
        }
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "tool_result");
        block.put("tool_use_id", toolUseId);
        block.put("content", content == null ? "" : StringUtils.toJson(content));
        if (isError) {
            block.put("is_error", true);
        }
        return block;
    }

    @Override
    public Map<String, ?> filterToFirstToolCall(Map<String, ?> dataAsMap, Map<String, String> parameters) {
        if (dataAsMap == null || !(dataAsMap.get("content") instanceof List<?> content) || content.size() <= 1) {
            return dataAsMap;
        }
        List<Object> filteredContent = new ArrayList<>();
        boolean foundFirstToolUse = false;
        for (Object item : content) {
            boolean toolUse = isToolUse(item);
            if (!toolUse || !foundFirstToolUse) {
                filteredContent.add(item);
            }
            foundFirstToolUse = foundFirstToolUse || toolUse;
        }
        if (filteredContent.size() == content.size()) {
            return dataAsMap;
        }
        log.info("LLM suggested {} content blocks with several tool calls. Selected first tool call.", content.size());
        Map<String, Object> filtered = new LinkedHashMap<String, Object>(dataAsMap);
        filtered.put("content", filteredContent);
        return filtered;
    }

    /**
     * Anthropic reports {@code input_tokens} without the cached part, which comes separately as
     * {@code cache_read_input_tokens} and {@code cache_creation_input_tokens}. The total adds all four.
     */
    @Override
    @SuppressWarnings("unchecked")
    public TokenUsage extractTokenUsage(Map<String, ?> llmResponseDataAsMap) {
        if (llmResponseDataAsMap == null || !(llmResponseDataAsMap.get("usage") instanceof Map)) {
            return null;
        }
        try {
            Map<String, Object> usageMap = (Map<String, Object>) llmResponseDataAsMap.get("usage");
            Long inputTokens = AgentUtils.getLongValue(usageMap, "input_tokens");
            Long outputTokens = AgentUtils.getLongValue(usageMap, "output_tokens");
            Long cacheReadTokens = AgentUtils.getLongValue(usageMap, "cache_read_input_tokens");
            Long cacheCreationTokens = AgentUtils.getLongValue(usageMap, "cache_creation_input_tokens");
            return TokenUsage
                .builder()
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(sum(inputTokens, outputTokens, cacheReadTokens, cacheCreationTokens))
                .cacheReadInputTokens(cacheReadTokens)
                .cacheCreationInputTokens(cacheCreationTokens)
                .build();
        } catch (Exception e) {
            return null;
        }
    }

    private static Long sum(Long... values) {
        Long total = null;
        for (Long value : values) {
            if (value != null) {
                total = total == null ? value : total + value;
            }
        }
        return total;
    }

    @Override
    public boolean supportsForcedToolCall() {
        return true;
    }

    @Override
    public String forcedToolConfigs(String toolName, String toolDescription, Map<String, Object> inputSchema) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", toolName);
        tool.put("description", toolDescription);
        tool.put("input_schema", inputSchema);
        Map<String, Object> toolChoice = new LinkedHashMap<>();
        toolChoice.put("type", "tool");
        toolChoice.put("name", toolName);
        return ", \"tools\": [" + StringUtils.toJson(tool) + "], \"tool_choice\": " + StringUtils.toJson(toolChoice);
    }

    @Override
    public String extractForcedToolInput(ModelTensorOutput modelTensorOutput, String toolName) {
        Map<String, ?> dataAsMap = modelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();
        for (Map<?, ?> toolUse : toolUseBlocks(dataAsMap)) {
            if (toolName.equals(toolUse.get(NAME))) {
                return StringUtils.toJson(toolUse.get(INPUT));
            }
        }
        return null;
    }
}
