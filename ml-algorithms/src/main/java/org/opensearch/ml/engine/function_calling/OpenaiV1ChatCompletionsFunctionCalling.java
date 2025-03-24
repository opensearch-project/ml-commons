package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.removeJsonPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;

import com.jayway.jsonpath.JsonPath;

import lombok.Data;

public class OpenaiV1ChatCompletionsFunctionCalling implements FunctionCalling {
    private static final String FINISH_REASON_PATH = "$.choices[0].finish_reason";
    private static final String FINISH_REASON = "tool_calls";
    private static final String CALL_PATH = "$.choices[0].message.tool_calls";
    private static final String NAME = "function.name";
    private static final String INPUT = "function.arguments";
    private static final String ID_PATH = "id";
    private static final String TOOL_ERROR = "tool_error";
    private static final String TOOL_RESULT = "tool_result";

    @Override
    public void configure(Map<String, String> params) {
        params
            .put(
                "tool_template",
                "{\"type\": \"function\", \"function\": { \"name\": \"${tool.name}\", \"description\": \"${tool.description}\", \"parameters\": ${tool.attributes.input_schema}, \"strict\": ${tool.attributes.strict:-false} } }"
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
        String llmFinishReason = JsonPath.read(dataAsMap, FINISH_REASON_PATH);
        if (!llmFinishReason.contentEquals(FINISH_REASON)) {
            return output;
        }
        List toolCalls = JsonPath.read(dataAsMap, CALL_PATH);
        if (CollectionUtils.isEmpty(toolCalls)) {
            return output;
        }
        for (Object call : toolCalls) {
            String toolName = JsonPath.read(call, parameters.get(NAME));
            String toolInput = StringUtils.toJson(JsonPath.read(call, parameters.get(INPUT)));
            String toolCallId = JsonPath.read(call, parameters.get(ID_PATH));
            output.add(Map.of("tool_name", toolName, "tool_input", toolInput, "tool_call_id", toolCallId));
        }
        return output;
    }

    @Override
    public LLMMessage supply(List<Map<String, String>> toolResults) {
        LLMMessage toolMessage = new LLMMessage();
        for (Map toolResult : toolResults) {
            String toolUseId = (String) toolResult.get(ID_PATH);
            if (toolUseId == null) {
                continue;
            }
            ToolResult result = new ToolResult();
            result.setRole("tool");
            result.setToolUseId(toolUseId);
            result.setContent((String) toolResult.get(TOOL_RESULT));
            toolMessage.getContent().add(result);
        }

        return toolMessage;
    }

    @Data
    public static class ToolResult {
        private String role;
        private String toolUseId;
        private String content;
    }
}
