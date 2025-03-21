package org.opensearch.ml.engine.function_calling;

import com.jayway.jsonpath.JsonPath;
import lombok.Data;
import org.opensearch.core.common.util.CollectionUtils;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.removeJsonPath;

public class BedrockConverseFunctionCalling implements FunctionCalling {
    private static final String FINISH_REASON_PATH = "$.stopReason";
    private static final String FINISH_REASON = "tool_use";
    private static final String CALL_PATH = "$.output.message.content[*].toolUse";
    private static final String NAME = "name";
    private static final String INPUT = "input";
    private static final String ID_PATH = "toolUseId";
    private static final String TOOL_ERROR = "tool_error";
    private static final String TOOL_RESULT = "tool_result";

    @Override
    public void configure(Map<String, String> params) {
        params.put("tool_template", "{\"toolSpec\":{\"name\":\"${tool.name}\",\"description\":\"${tool.description}\",\"inputSchema\": {\"json\": ${tool.attributes.input_schema} } }}");
        params.put("tool_configs", ", \"toolConfig\": {\"tools\": [${parameters._tools:-}]}");
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
            result.setToolUseId(toolUseId);
            result.getContent().add(Map.of("text", toolResult.get(TOOL_RESULT)));
            if (toolResult.containsKey(TOOL_ERROR)) {
                result.setStatus("error");
            }
            toolMessage.getContent().add(result);
        }

        return toolMessage;
    }

    @Data
    public static class ToolResult {
        private String toolUseId;
        private List<Map<String, Object>> content = new ArrayList<>();
        private String status;
    }
}
