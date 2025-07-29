/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINAL_RESPONSE_POST_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.NO_ESCAPE_PARAMS;
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

public class BedrockConverseDeepseekR1FunctionCalling implements FunctionCalling {
    public static final String FINISH_REASON_PATH = "stop_reason";
    public static final String FINISH_REASON = "tool_use";
    public static final String CALL_PATH = "tool_calls";
    public static final String NAME = "tool_name";
    public static final String INPUT = "input";
    public static final String ID_PATH = "id";
    public static final String TOOL_ERROR = "tool_error";
    public static final String BEDROCK_DEEPSEEK_R1_TOOL_TEMPLATE =
        "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}";

    @Override
    public void configure(Map<String, String> params) {
        if (!params.containsKey(NO_ESCAPE_PARAMS)) {
            params.put(NO_ESCAPE_PARAMS, "_chat_history,_interactions");
        }
        params.put(LLM_RESPONSE_FILTER, "$.output.message.content[0].text");
        params.put(LLM_FINAL_RESPONSE_POST_FILTER, "$.message.content[0].text");

        params.put(TOOL_TEMPLATE, BEDROCK_DEEPSEEK_R1_TOOL_TEMPLATE);
        params.put(TOOL_CALLS_PATH, "_llm_response.tool_calls");
        params.put(TOOL_CALLS_TOOL_NAME, "tool_name");
        params.put(TOOL_CALLS_TOOL_INPUT, "input");
        params.put(TOOL_CALL_ID_PATH, "id");

        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH, "$.output.message");
        params.put(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH, "[ \"$.output.message.content[?(@.reasoningContent)]\" ]");
        params
            .put(
                INTERACTION_TEMPLATE_TOOL_RESPONSE,
                "{\"role\":\"user\",\"content\":[ {\"text\":\"{\\\"tool_call_id\\\":\\\"${_interactions.tool_call_id}\\\",\\\"tool_result\\\": \\\"${_interactions.tool_response}\\\"\"} ]}"
            );

        params.put(CHAT_HISTORY_QUESTION_TEMPLATE, "{\"role\":\"user\",\"content\":[{\"text\":\"${_chat_history.message.question}\"}]}");
        params
            .put(CHAT_HISTORY_RESPONSE_TEMPLATE, "{\"role\":\"assistant\",\"content\":[{\"text\":\"${_chat_history.message.response}\"}]}");

        params.put(LLM_FINISH_REASON_PATH, "_llm_response.stop_reason");
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
        Object response = JsonPath.read(dataAsMap, parameters.get(LLM_RESPONSE_FILTER));
        Map<String, Object> llmResponse = StringUtils.fromJson(response.toString(), "response");
        String llmFinishReason = JsonPath.read(llmResponse, FINISH_REASON_PATH);
        if (!llmFinishReason.contentEquals(FINISH_REASON)) {
            return output;
        }
        List toolCalls = JsonPath.read(llmResponse, CALL_PATH);
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

            String textJson = StringUtils.toJson(Map.of(TOOL_CALL_ID, toolUseId, TOOL_RESULT, toolResult.get(TOOL_RESULT)));
            toolMessage.getContent().add(Map.of("text", textJson));
        }

        return List.of(toolMessage);
    }
}
