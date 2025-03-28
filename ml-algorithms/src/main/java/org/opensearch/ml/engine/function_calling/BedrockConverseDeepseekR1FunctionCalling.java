package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.removeJsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.JsonPath;

public class BedrockConverseDeepseekR1FunctionCalling implements FunctionCalling {
    private static final String FINISH_REASON_PATH = "stop_reason";
    private static final String FINISH_REASON = "tool_use";
    private static final String CALL_PATH = "tool_calls";
    private static final String NAME = "tool_name";
    private static final String INPUT = "input";
    private static final String ID_PATH = "id";
    private static final String TOOL_ERROR = "tool_error";

    @Override
    public void configure(Map<String, String> params) {
        params
            .put(
                "tool_template",
                "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}"
            );
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
            toolMessage.getContent().add(Map.of("text", Map.of(TOOL_CALL_ID, toolUseId, TOOL_RESULT, toolResult.get(TOOL_RESULT))));
        }

        return List.of(toolMessage);
    }
}
