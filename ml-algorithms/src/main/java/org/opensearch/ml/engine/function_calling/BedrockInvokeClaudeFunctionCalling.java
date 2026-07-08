/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.common.utils.ToolUtils.NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.DEFAULT_NO_ESCAPE_PARAMS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_NAME;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.INTERACTION_TEMPLATE_TOOL_RESPONSE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.engine.algorithms.agent.AgentUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import lombok.Data;
import lombok.extern.log4j.Log4j2;

/**
 * Function calling implementation for Bedrock InvokeModel API with Claude models.
 * Uses Claude's native Messages API format instead of Bedrock's Converse API format.
 * Supports both flat format (non-streaming) and wrapped format (streaming tool calls).
 */
@Log4j2
public class BedrockInvokeClaudeFunctionCalling implements FunctionCalling {
    // Wrapped format paths (streaming tool calls)
    public static final String FINISH_REASON_PATH_WRAPPED = "$.stopReason";
    public static final String CALL_PATH_WRAPPED = "$.output.message.content[?(@.type=='tool_use')]";
    public static final String CONTENT_PATH_WRAPPED = "$.output.message.content";

    // Flat format paths (non-streaming responses)
    public static final String FINISH_REASON_PATH_FLAT = "$.stop_reason";
    public static final String CALL_PATH_FLAT = "$.content[?(@.type=='tool_use')]";
    public static final String CONTENT_PATH_FLAT = "$.content";

    public static final String FINISH_REASON = "tool_use";
    public static final String NAME = "name";
    public static final String INPUT = "input";
    public static final String ID_PATH = "id";
    public static final String TOOL_ERROR = "tool_error";

    // Claude Messages API format for tools (no toolSpec wrapper, no json wrapper for input_schema)
    public static final String CLAUDE_MESSAGES_TOOL_TEMPLATE =
        "{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"input_schema\": ${tool.attributes.input_schema} }";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
        }
        // Use wrapped format paths (to match streaming tool call responses)
        // Non-streaming flat responses are wrapped in handle() before processing
        // Use filter to find text blocks dynamically (compaction may be at index 0)
        params.put(LLM_RESPONSE_FILTER, "$.output.message.content[?(@.type=='text')][0].text");

        params.put(TOOL_TEMPLATE, CLAUDE_MESSAGES_TOOL_TEMPLATE);
        params.put(TOOL_CALLS_PATH, "$.output.message.content[?(@.type=='tool_use')]");
        params.put(TOOL_CALLS_TOOL_NAME, "name");
        params.put(TOOL_CALLS_TOOL_INPUT, "input");
        params.put(TOOL_CALL_ID_PATH, "id");

        // Claude Messages API format: tools are at top level, not wrapped in toolConfig
        params.put("tool_configs", ", \"tools\": [${parameters._tools:-}]");

        // For wrapped format, the assistant message is at output.message
        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$.output.message");

        // Exclude metadata fields - use both wrapped and flat field names
        params
            .put(
                INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH,
                "[\"$.model\", \"$.id\", \"$.type\", \"$.stop_reason\", \"$.stopReason\", \"$.stop_sequence\", \"$.usage\"]"
            );

        // Claude Messages API format for tool results - content blocks need type field
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"${_interactions.tool_call_id}\",\"content\":\"${_interactions.tool_response}\"}]}"
            );

        // Claude Messages API format for chat history - content blocks need type field
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

        params.put(LLM_FINISH_REASON_PATH, "$.stopReason");
        params.put(LLM_FINISH_REASON_TOOL_USE, "tool_use");
    }

    @Override
    public List<Map<String, String>> handle(ModelTensorOutput tmpModelTensorOutput, Map<String, String> parameters) {
        List<Map<String, String>> output = new ArrayList<>();
        Map<String, ?> dataAsMap = tmpModelTensorOutput.getMlModelOutputs().get(0).getMlModelTensors().get(0).getDataAsMap();

        // Wrap flat responses to match wrapped format expected by configure() paths
        boolean isWrapped = isWrappedFormat(dataAsMap);
        if (!isWrapped) {
            log.debug("Wrapping flat non-streaming response to match streaming format");
            dataAsMap = wrapFlatResponse(dataAsMap);
        }

        // Now always use wrapped format paths
        String llmFinishReason = JsonPath.read(dataAsMap, FINISH_REASON_PATH_WRAPPED);
        if (!llmFinishReason.contentEquals(FINISH_REASON)) {
            return output;
        }
        List toolCalls = JsonPath.read(dataAsMap, CALL_PATH_WRAPPED);
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

    /**
     * Detect if the response is in wrapped format (streaming tool calls) or flat format (non-streaming).
     * Wrapped format has "output" at root level, flat format has "content" or "role" at root level.
     */
    private boolean isWrappedFormat(Map<String, ?> dataAsMap) {
        return dataAsMap.containsKey("output");
    }

    /**
     * Wrap flat Claude Messages API response into the wrapped format used by streaming.
     * Converts: {"role": "assistant", "content": [...], "stop_reason": "tool_use"}
     * To: {"output": {"message": {"role": "assistant", "content": [...]}}, "stopReason": "tool_use"}
     */
    private Map<String, Object> wrapFlatResponse(Map<String, ?> flatResponse) {
        Object role = flatResponse.get("role");
        Object content = flatResponse.get("content");
        Object stopReasonObj = flatResponse.get("stop_reason");

        Map<String, Object> message = Map.of("role", role != null ? role : "assistant", "content", content != null ? content : List.of());

        String stopReason = stopReasonObj != null ? String.valueOf(stopReasonObj) : "";

        return Map.of("output", Map.of("message", message), "stopReason", stopReason);
    }

    @Override
    public List<LLMMessage> supply(List<Map<String, Object>> toolResults) {
        ClaudeMessage toolMessage = new ClaudeMessage();
        for (Map toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(TOOL_CALL_ID);
            if (toolUseId == null) {
                continue;
            }
            ToolResult result = new ToolResult();
            result.setType("tool_result");
            result.setToolUseId(toolUseId);
            result.setContent(String.valueOf(toolResult.get(TOOL_RESULT)));
            if (toolResult.containsKey(TOOL_ERROR)) {
                result.setError(true);
            }
            toolMessage.getContent().add(result);
        }

        return List.of(toolMessage);
    }

    @Override
    public TokenUsage extractTokenUsage(Map<String, ?> llmResponseDataAsMap) {
        if (llmResponseDataAsMap == null) {
            return null;
        }

        try {
            // Both wrapped (streaming) and flat (non-streaming) formats have usage at root level
            // (not nested inside output.message), so we can access it directly
            Object usageObj = llmResponseDataAsMap.get("usage");
            if (!(usageObj instanceof Map)) {
                return null;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> usageMap = (Map<String, Object>) usageObj;

            // Claude InvokeModel API uses snake_case field names (input_tokens, output_tokens)
            // Different from Bedrock Converse API which uses camelCase (inputTokens, outputTokens)

            // Check if iterations array exists - if it does, calculate totals from iterations
            Object iterationsObj = usageMap.get("iterations");
            Long finalInputTokens;
            Long finalOutputTokens;
            Long compactionInputTokens = null;
            Long compactionOutputTokens = null;
            Map<String, Long> additionalUsage = null;

            if (iterationsObj instanceof List) {
                List<?> iterations = (List<?>) iterationsObj;
                additionalUsage = new HashMap<>();

                // Store iteration count for tracking
                additionalUsage.put("iterations_count", (long) iterations.size());

                // Calculate tokens from iterations array
                // - Total tokens = sum of ALL iterations (compaction + message)
                // - Compaction tokens = sum of only compaction type iterations
                long totalInputFromIterations = 0;
                long totalOutputFromIterations = 0;
                long compactionInputFromIterations = 0;
                long compactionOutputFromIterations = 0;

                for (Object iterObj : iterations) {
                    if (iterObj instanceof Map) {
                        Map<String, Object> iter = (Map<String, Object>) iterObj;
                        String iterType = (String) iter.get("type");
                        Long inputTokens = AgentUtils.getLongValue(iter, "input_tokens");
                        Long outputTokens = AgentUtils.getLongValue(iter, "output_tokens");

                        // Sum all iterations for total
                        if (inputTokens != null) {
                            totalInputFromIterations += inputTokens;
                        }
                        if (outputTokens != null) {
                            totalOutputFromIterations += outputTokens;
                        }

                        // Sum only compaction iterations separately
                        if ("compaction".equals(iterType)) {
                            if (inputTokens != null) {
                                compactionInputFromIterations += inputTokens;
                            }
                            if (outputTokens != null) {
                                compactionOutputFromIterations += outputTokens;
                            }
                        }
                    }
                }

                // Use totals from iterations (sum of all iterations)
                finalInputTokens = totalInputFromIterations;
                finalOutputTokens = totalOutputFromIterations;

                // Store compaction tokens separately if any compaction occurred
                if (compactionInputFromIterations > 0) {
                    compactionInputTokens = compactionInputFromIterations;
                    additionalUsage.put("compaction_input_tokens", compactionInputFromIterations);
                }
                if (compactionOutputFromIterations > 0) {
                    compactionOutputTokens = compactionOutputFromIterations;
                    additionalUsage.put("compaction_output_tokens", compactionOutputFromIterations);
                }
            } else {
                // No iterations array - use top-level tokens (no compaction occurred)
                finalInputTokens = AgentUtils.getLongValue(usageMap, "input_tokens");
                finalOutputTokens = AgentUtils.getLongValue(usageMap, "output_tokens");
            }

            TokenUsage.TokenUsageBuilder builder = TokenUsage
                .builder()
                .inputTokens(finalInputTokens)
                .outputTokens(finalOutputTokens)
                .totalTokens(finalInputTokens != null && finalOutputTokens != null ? finalInputTokens + finalOutputTokens : null);

            // Claude cache tokens (prompt caching feature)
            builder.cacheReadInputTokens(AgentUtils.getLongValue(usageMap, "cache_read_input_tokens"));
            builder.cacheCreationInputTokens(AgentUtils.getLongValue(usageMap, "cache_creation_input_tokens"));

            // Add additional usage metrics if present
            if (additionalUsage != null && !additionalUsage.isEmpty()) {
                builder.additionalUsage(additionalUsage);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Failed to extract token usage from Claude InvokeModel response", e);
            return null;
        }
    }

    @Override
    public Map<String, ?> filterToFirstToolCall(Map<String, ?> dataAsMap, Map<String, String> parameters) {
        try {
            // Wrap flat responses to match expected format
            boolean isWrapped = isWrappedFormat(dataAsMap);
            if (!isWrapped) {
                log.debug("filterToFirstToolCall: Wrapping flat response");
                dataAsMap = wrapFlatResponse(dataAsMap);
            }

            // Always use wrapped path
            List<Object> contentList = JsonPath.read(dataAsMap, CONTENT_PATH_WRAPPED);
            if (contentList == null || contentList.size() <= 1) {
                return dataAsMap;
            }

            // Keep only text and first tool_use
            List<Object> filteredContent = new ArrayList<>();
            List<String> allToolNames = new ArrayList<>();
            String selectedToolName = null;
            boolean foundFirstToolUse = false;

            for (Object item : contentList) {
                if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    if ("tool_use".equals(itemMap.get("type"))) {
                        String toolName = String.valueOf(itemMap.get("name"));
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
            context.set(CONTENT_PATH_WRAPPED, filteredContent);
            return context.json();
        } catch (Exception e) {
            log.error("Failed to filter out to only first tool call", e);
            return dataAsMap;
        }
    }

    @Data
    public static class ToolResult {
        private String type;

        @JsonProperty("tool_use_id")
        private String toolUseId;

        private String content;

        @JsonProperty("is_error")
        private boolean error;
    }

    @Data
    public static class ClaudeMessage implements LLMMessage {
        private String role = "user";
        private List<Object> content = new ArrayList<>();

        public String getResponse() {
            String roleToUse = role != null ? role : "user";
            List<Object> contentToUse = content != null ? content : new ArrayList<>();
            return StringUtils.toJson(Map.of("role", roleToUse, "content", contentToUse));
        }
    }
}
