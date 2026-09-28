/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.json.JsonXContent;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

@SuppressWarnings("unchecked")
public class ForcedToolCallTests {

    private static final String TOOL = "FillTemplate";
    private static final Map<String, Object> SCHEMA = Map
        .of("type", "object", "properties", Map.of("q", Map.of("type", "string", "description", "say \"hi\"")));

    /** A tool_configs fragment starts with a comma; wrapped in braces it must be a JSON object. */
    private static Map<String, Object> parseFragment(String fragment) {
        assertTrue(fragment.startsWith(","));
        return XContentHelper.convertToMap(JsonXContent.jsonXContent, "{" + fragment.substring(1) + "}", false);
    }

    private static ModelTensorOutput output(Map<String, ?> dataAsMap) {
        ModelTensor tensor = ModelTensor.builder().dataAsMap(dataAsMap).build();
        return ModelTensorOutput.builder().mlModelOutputs(List.of(ModelTensors.builder().mlModelTensors(List.of(tensor)).build())).build();
    }

    @Test
    public void bedrock_forcesTheNamedTool() {
        FunctionCalling fc = new BedrockConverseFunctionCalling();
        assertTrue(fc.supportsForcedToolCall());
        Map<String, Object> body = parseFragment(fc.forcedToolConfigs(TOOL, "desc", SCHEMA));

        Map<String, Object> toolConfig = (Map<String, Object>) body.get("toolConfig");
        Map<String, Object> toolSpec = (Map<String, Object>) ((List<Map<String, Object>>) toolConfig.get("tools")).get(0).get("toolSpec");
        assertEquals(TOOL, toolSpec.get("name"));
        assertEquals(SCHEMA, ((Map<String, Object>) toolSpec.get("inputSchema")).get("json"));
        assertEquals(Map.of("tool", Map.of("name", TOOL)), toolConfig.get("toolChoice"));
    }

    @Test
    public void bedrock_extractsToolInput() {
        FunctionCalling fc = new BedrockConverseFunctionCalling();
        Map<String, Object> response = Map
            .of(
                "stopReason",
                "tool_use",
                "output",
                Map
                    .of(
                        "message",
                        Map
                            .of(
                                "content",
                                List
                                    .of(
                                        Map.of("toolUse", Map.of("name", "Other", "input", Map.of("x", 1), "toolUseId", "a")),
                                        Map.of("toolUse", Map.of("name", TOOL, "input", Map.of("q", "shoes", "size", 5), "toolUseId", "b"))
                                    )
                            )
                    )
            );
        Map<String, Object> input = XContentHelper
            .convertToMap(JsonXContent.jsonXContent, fc.extractForcedToolInput(output(response), TOOL), false);
        assertEquals("shoes", input.get("q"));
        assertEquals(5, input.get("size"));
    }

    @Test
    public void bedrock_noToolCall_returnsNull() {
        FunctionCalling fc = new BedrockConverseFunctionCalling();
        assertNull(fc.extractForcedToolInput(output(Map.of("stopReason", "end_turn")), TOOL));
        Map<String, Object> textOnly = Map.of("output", Map.of("message", Map.of("content", List.of(Map.of("text", "hello")))));
        assertNull(fc.extractForcedToolInput(output(textOnly), TOOL));
    }

    @Test
    public void openai_forcesTheNamedTool() {
        FunctionCalling fc = new OpenaiV1ChatCompletionsFunctionCalling();
        assertTrue(fc.supportsForcedToolCall());
        Map<String, Object> body = parseFragment(fc.forcedToolConfigs(TOOL, "desc", SCHEMA));

        Map<String, Object> function = (Map<String, Object>) ((List<Map<String, Object>>) body.get("tools")).get(0).get("function");
        assertEquals(TOOL, function.get("name"));
        assertEquals(SCHEMA, function.get("parameters"));
        assertEquals(Map.of("type", "function", "function", Map.of("name", TOOL)), body.get("tool_choice"));
        assertEquals(false, body.get("parallel_tool_calls"));
    }

    @Test
    public void openai_extractsToolInput_whenFinishReasonIsStop() {
        // A forced tool choice is reported as a normal stop, which handle() would skip.
        FunctionCalling fc = new OpenaiV1ChatCompletionsFunctionCalling();
        Map<String, Object> call = Map
            .of("id", "c1", "type", "function", "function", Map.of("name", TOOL, "arguments", "{\"q\":\"shoes\"}"));
        Map<String, Object> response = Map
            .of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("role", "assistant", "tool_calls", List.of(call)))));
        assertEquals("{\"q\":\"shoes\"}", fc.extractForcedToolInput(output(response), TOOL));
    }

    @Test
    public void openai_noToolCall_returnsNull() {
        FunctionCalling fc = new OpenaiV1ChatCompletionsFunctionCalling();
        Map<String, Object> response = Map.of("choices", List.of(Map.of("finish_reason", "stop", "message", Map.of("content", "hi"))));
        assertNull(fc.extractForcedToolInput(output(response), TOOL));
    }

    @Test
    public void gemini_forcesTheNamedTool() {
        FunctionCalling fc = new GeminiV1BetaGenerateContentFunctionCalling();
        assertTrue(fc.supportsForcedToolCall());
        Map<String, Object> body = parseFragment(fc.forcedToolConfigs(TOOL, "desc", SCHEMA));

        Map<String, Object> declaration = ((List<Map<String, Object>>) ((List<Map<String, Object>>) body.get("tools"))
            .get(0)
            .get("functionDeclarations")).get(0);
        assertEquals(TOOL, declaration.get("name"));
        Map<String, Object> callingConfig = (Map<String, Object>) ((Map<String, Object>) body.get("toolConfig"))
            .get("functionCallingConfig");
        assertEquals("ANY", callingConfig.get("mode"));
        assertEquals(List.of(TOOL), callingConfig.get("allowedFunctionNames"));
    }

    @Test
    public void gemini_turnsNonStringEnumsIntoStringEnums() {
        Map<String, Object> schema = Map
            .of(
                "type",
                "object",
                "properties",
                Map
                    .of(
                        "size",
                        Map.of("type", "number", "enum", List.of(5, 10)),
                        "mixed",
                        Map.of("enum", List.of(1, "a")),
                        "order",
                        Map.of("type", "string", "enum", List.of("asc", "desc"))
                    )
            );
        FunctionCalling fc = new GeminiV1BetaGenerateContentFunctionCalling();
        Map<String, Object> body = parseFragment(fc.forcedToolConfigs(TOOL, "desc", schema));
        Map<String, Object> declaration = ((List<Map<String, Object>>) ((List<Map<String, Object>>) body.get("tools"))
            .get(0)
            .get("functionDeclarations")).get(0);
        Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) declaration.get("parameters")).get("properties");
        assertEquals(Map.of("type", "string", "enum", List.of("5", "10")), props.get("size"));
        assertEquals(Map.of("type", "string", "enum", List.of("1", "a")), props.get("mixed"));
        assertEquals(Map.of("type", "string", "enum", List.of("asc", "desc")), props.get("order"));
    }

    @Test
    public void gemini_extractsToolInput() {
        FunctionCalling fc = new GeminiV1BetaGenerateContentFunctionCalling();
        Map<String, Object> response = Map
            .of(
                "candidates",
                List
                    .of(
                        Map
                            .of(
                                "content",
                                Map.of("parts", List.of(Map.of("functionCall", Map.of("name", TOOL, "args", Map.of("q", "shoes")))))
                            )
                    )
            );
        assertEquals("{\"q\":\"shoes\"}", fc.extractForcedToolInput(output(response), TOOL));
    }

    @Test
    public void deepseek_doesNotSupportForcedToolCalls() {
        FunctionCalling fc = new BedrockConverseDeepseekR1FunctionCalling();
        assertFalse(fc.supportsForcedToolCall());
        assertThrows(UnsupportedOperationException.class, () -> fc.forcedToolConfigs(TOOL, "desc", SCHEMA));
        assertThrows(UnsupportedOperationException.class, () -> fc.extractForcedToolInput(output(Map.of()), TOOL));
    }
}
