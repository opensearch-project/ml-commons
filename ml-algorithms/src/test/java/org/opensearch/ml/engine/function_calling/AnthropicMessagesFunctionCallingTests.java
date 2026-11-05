/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_ANTHROPIC_V1_MESSAGES;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_TEMPLATE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.agent.AnthropicMessagesModelProvider;
import org.opensearch.ml.common.agent.TokenUsage;
import org.opensearch.ml.common.input.execute.agent.Message;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.utils.StringUtils;

public class AnthropicMessagesFunctionCallingTests {

    private FunctionCalling functionCalling;

    @Before
    public void setUp() {
        functionCalling = FunctionCallingFactory.create(LLM_INTERFACE_ANTHROPIC_V1_MESSAGES);
    }

    @Test
    public void testFactoryCreatesAnthropicFunctionCalling() {
        assertTrue(functionCalling instanceof AnthropicMessagesFunctionCalling);
        assertTrue(FunctionCallingFactory.create(" Anthropic/V1/Messages ") instanceof AnthropicMessagesFunctionCalling);
    }

    @Test
    public void testConfigure() {
        Map<String, String> parameters = new HashMap<>();

        functionCalling.configure(parameters);

        assertEquals(AnthropicMessagesFunctionCalling.ANTHROPIC_TOOL_TEMPLATE, parameters.get(TOOL_TEMPLATE));
        assertEquals(", \"tools\": [${parameters._tools:-}]", parameters.get("tool_configs"));
        assertEquals("$.content[0].text", parameters.get(LLM_RESPONSE_FILTER));
        assertEquals("$.stop_reason", parameters.get(LLM_FINISH_REASON_PATH));
        assertEquals("tool_use", parameters.get(LLM_FINISH_REASON_TOOL_USE));
        assertEquals(AnthropicMessagesFunctionCalling.CALL_PATH, parameters.get(TOOL_CALLS_PATH));
        assertEquals("$.usage", parameters.get("token_usage_path"));
        assertTrue(parameters.get("no_escape_params").contains("_tools"));
    }

    @Test
    public void testHandle_ToolUse() {
        Map<String, Object> response = response(
            "tool_use",
            List
                .of(
                    Map.of("type", "text", "text", "Let me look"),
                    Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of("query", "x")),
                    Map.of("type", "tool_use", "id", "toolu_2", "name", "lookup", "input", Map.of())
                )
        );

        List<Map<String, String>> toolCalls = functionCalling.handle(output(response), new HashMap<>());

        assertEquals(2, toolCalls.size());
        assertEquals(Map.of("tool_name", "search", "tool_input", "{\"query\":\"x\"}", "tool_call_id", "toolu_1"), toolCalls.get(0));
        assertEquals(Map.of("tool_name", "lookup", "tool_input", "{}", "tool_call_id", "toolu_2"), toolCalls.get(1));
    }

    @Test
    public void testHandle_EndTurnAndMaxTokensReturnNoCalls() {
        List<Object> content = List.of(Map.of("type", "text", "text", "Done"));

        assertTrue(functionCalling.handle(output(response("end_turn", content)), new HashMap<>()).isEmpty());
        assertTrue(functionCalling.handle(output(response("max_tokens", content)), new HashMap<>()).isEmpty());
    }

    @Test
    public void testSupply_OneUserMessagePerToolResult() {
        List<Map<String, Object>> toolResults = List
            .of(
                Map.of(TOOL_CALL_ID, "toolu_1", TOOL_RESULT, Map.of("text", "42")),
                Map.of(TOOL_CALL_ID, "toolu_2", TOOL_RESULT, Map.of("text", Map.of("hits", 3))),
                Map.of(TOOL_RESULT, Map.of("text", "no id, skipped"))
            );

        List<LLMMessage> messages = functionCalling.supply(toolResults);

        assertEquals(2, messages.size());
        assertEquals("user", messages.get(0).getRole());
        assertEquals(
            "{\"role\":\"user\",\"content\":[{\"type\":\"tool_result\",\"tool_use_id\":\"toolu_1\",\"content\":\"42\"}]}",
            messages.get(0).getResponse()
        );
        assertEquals(
            Map.of("type", "tool_result", "tool_use_id", "toolu_2", "content", "{\"hits\":3}"),
            ((List<?>) messages.get(1).getContent()).get(0)
        );
    }

    @Test
    public void testSupply_ErrorResult() {
        List<Map<String, Object>> toolResults = List
            .of(
                Map.of(TOOL_CALL_ID, "toolu_1", TOOL_RESULT, Map.of("error", "Tool not found: x")),
                Map.of(TOOL_CALL_ID, "toolu_2", TOOL_RESULT, Map.of("text", "partial"), AnthropicMessagesFunctionCalling.TOOL_ERROR, true)
            );

        List<LLMMessage> messages = functionCalling.supply(toolResults);

        assertEquals(
            Map.of("type", "tool_result", "tool_use_id", "toolu_1", "content", "Tool not found: x", "is_error", true),
            ((List<?>) messages.get(0).getContent()).get(0)
        );
        assertEquals(
            Map.of("type", "tool_result", "tool_use_id", "toolu_2", "content", "partial", "is_error", true),
            ((List<?>) messages.get(1).getContent()).get(0)
        );
    }

    @Test
    public void testSupply_ParsesBackToUnifiedToolMessage() {
        List<LLMMessage> messages = functionCalling.supply(List.of(Map.of(TOOL_CALL_ID, "toolu_1", TOOL_RESULT, Map.of("text", "42"))));

        Message message = new AnthropicMessagesModelProvider().parseToUnifiedMessage(messages.get(0).getResponse());

        assertEquals("tool", message.getRole());
        assertEquals("toolu_1", message.getToolCallId());
        assertEquals("42", message.getContent().get(0).getText());
    }

    @Test
    public void testFilterToFirstToolCall_MultipleToolCalls() {
        Map<String, Object> response = response(
            "tool_use",
            List
                .of(
                    Map.of("type", "text", "text", "Let me look"),
                    Map.of("type", "tool_use", "id", "toolu_1", "name", "search", "input", Map.of()),
                    Map.of("type", "tool_use", "id", "toolu_2", "name", "lookup", "input", Map.of())
                )
        );

        Map<String, ?> filtered = functionCalling.filterToFirstToolCall(response, new HashMap<>());

        List<?> content = (List<?>) filtered.get("content");
        assertEquals(2, content.size());
        assertEquals("toolu_1", ((Map<?, ?>) content.get(1)).get("id"));
        assertEquals("tool_use", filtered.get("stop_reason"));
        assertEquals(3, ((List<?>) response.get("content")).size());
    }

    @Test
    public void testFilterToFirstToolCall_NothingToFilter() {
        Map<String, Object> single = response(
            "tool_use",
            List.of(Map.of("type", "tool_use", "id", "toolu_1", "name", "a", "input", Map.of()))
        );
        Map<String, Object> textAndCall = response(
            "tool_use",
            List.of(Map.of("type", "text", "text", "hi"), Map.of("type", "tool_use", "id", "toolu_1", "name", "a", "input", Map.of()))
        );

        assertSame(single, functionCalling.filterToFirstToolCall(single, new HashMap<>()));
        assertSame(textAndCall, functionCalling.filterToFirstToolCall(textAndCall, new HashMap<>()));
        assertNull(functionCalling.filterToFirstToolCall(null, new HashMap<>()));
    }

    @Test
    public void testExtractTokenUsage_WithCacheTokens() {
        Map<String, Object> response = response("end_turn", List.of());
        response
            .put(
                "usage",
                Map.of("input_tokens", 50, "output_tokens", 20, "cache_read_input_tokens", 9000, "cache_creation_input_tokens", 400)
            );

        TokenUsage usage = functionCalling.extractTokenUsage(response);

        assertEquals(Long.valueOf(50), usage.getInputTokens());
        assertEquals(Long.valueOf(20), usage.getOutputTokens());
        assertEquals(Long.valueOf(9000), usage.getCacheReadInputTokens());
        assertEquals(Long.valueOf(400), usage.getCacheCreationInputTokens());
        assertEquals(Long.valueOf(9470), usage.getTotalTokens());
    }

    @Test
    public void testExtractTokenUsage_WithoutCacheTokens() {
        Map<String, Object> response = response("end_turn", List.of());
        response.put("usage", Map.of("input_tokens", 50, "output_tokens", 20));

        TokenUsage usage = functionCalling.extractTokenUsage(response);

        assertNull(usage.getCacheReadInputTokens());
        assertNull(usage.getCacheCreationInputTokens());
        assertEquals(Long.valueOf(70), usage.getTotalTokens());
    }

    @Test
    public void testExtractTokenUsage_MissingUsage() {
        assertNull(functionCalling.extractTokenUsage(null));
        assertNull(functionCalling.extractTokenUsage(response("end_turn", List.of())));
    }

    @Test
    public void testForcedToolCall() {
        assertTrue(functionCalling.supportsForcedToolCall());
        assertFalse(functionCalling.supportsStrictSchema());

        String toolConfigs = functionCalling.forcedToolConfigs("fill", "Fill the template", Map.of("type", "object"));

        assertEquals(
            ", \"tools\": [{\"name\":\"fill\",\"description\":\"Fill the template\",\"input_schema\":{\"type\":\"object\"}}],"
                + " \"tool_choice\": {\"type\":\"tool\",\"name\":\"fill\"}",
            toolConfigs
        );
        Map<String, Object> response = response(
            "tool_use",
            List.of(Map.of("type", "tool_use", "id", "toolu_1", "name", "fill", "input", Map.of("size", 10)))
        );
        assertEquals("{\"size\":10}", functionCalling.extractForcedToolInput(output(response), "fill"));
        assertNull(functionCalling.extractForcedToolInput(output(response), "other"));
    }

    @Test
    public void testAnthropicMessage_DefaultsAndResponse() {
        AnthropicMessage message = new AnthropicMessage();

        assertEquals("user", message.getRole());
        assertTrue(message.getContent().isEmpty());
        assertEquals("{\"role\":\"user\",\"content\":[]}", message.getResponse());
        assertTrue(StringUtils.isJson(new AnthropicMessage(null, List.of(Map.of("type", "text", "text", "x"))).getResponse()));
    }

    private static Map<String, Object> response(String stopReason, List<?> content) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "msg_1");
        response.put("type", "message");
        response.put("role", "assistant");
        response.put("content", content);
        response.put("stop_reason", stopReason);
        return response;
    }

    private static ModelTensorOutput output(Map<String, Object> dataAsMap) {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(dataAsMap).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(List.of(modelTensor)).build();
        return ModelTensorOutput.builder().mlModelOutputs(List.of(modelTensors)).build();
    }
}
