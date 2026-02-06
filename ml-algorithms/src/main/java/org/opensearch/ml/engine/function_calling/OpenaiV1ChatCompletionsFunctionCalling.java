/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

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

import com.jayway.jsonpath.JsonPath;

public class OpenaiV1ChatCompletionsFunctionCalling implements FunctionCalling {
    public static final String FINISH_REASON_PATH = "$.choices[0].finish_reason";
    public static final String FINISH_REASON = "tool_calls";
    public static final String CALL_PATH = "$.choices[0].message.tool_calls";
    public static final String NAME = "function.name";
    public static final String INPUT = "function.arguments";
    public static final String ID_PATH = "id";
    public static final String OPENAI_V1_CHAT_COMPLETION_TEMPLATE =
        "{\"type\": \"function\", \"function\": { \"name\": \"${tool.name}\", \"description\": \"${tool.description}\", \"parameters\": ${tool.attributes.input_schema}, \"strict\": ${tool.attributes.strict:-false} } }";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, DEFAULT_NO_ESCAPE_PARAMS);
        }
        params.put(LLM_RESPONSE_FILTER, "$.choices[0].message.content");

        params.put(TOOL_TEMPLATE, OPENAI_V1_CHAT_COMPLETION_TEMPLATE);
        params.put(TOOL_CALLS_PATH, "$.choices[0].message.tool_calls");
        params.put(TOOL_CALLS_TOOL_NAME, "function.name");
        params.put(TOOL_CALLS_TOOL_INPUT, "function.arguments");
        params.put(TOOL_CALL_ID_PATH, "id");
        params.put("tool_configs", ", \"tools\": [${parameters._tools:-}], \"parallel_tool_calls\": false");

        params.put("tool_choice", "auto");
        params.put("parallel_tool_calls", "false");

        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$.choices[0].message");
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"tool\",\"tool_call_id\":\"${_interactions.tool_call_id}\",\"content\":\"${_interactions.tool_response}\"}"
            );

        params.put(CHAT_HISTORY_QUESTION_TEMPLATE, "{\"role\":\"user\",\"content\":\"${_chat_history.message.question}\"}");
        params.put(CHAT_HISTORY_RESPONSE_TEMPLATE, "{\"role\":\"assistant\",\"content\":\"${_chat_history.message.response}\"}");

        params.put(LLM_FINISH_REASON_PATH, "$.choices[0].finish_reason");
        params.put(LLM_FINISH_REASON_TOOL_USE, "tool_calls");
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
        List<LLMMessage> messages = new ArrayList<>();
        for (Map toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(TOOL_CALL_ID);
            if (toolUseId == null) {
                continue;
            }
            OpenaiMessage toolMessage = new OpenaiMessage();
            toolMessage.setToolCallId(toolUseId);
            Map toolResultMap = (Map) toolResult.get(TOOL_RESULT);
            toolMessage.setContent((String) toolResultMap.get("text"));
            messages.add(toolMessage);
        }

        return messages;
    }
}
