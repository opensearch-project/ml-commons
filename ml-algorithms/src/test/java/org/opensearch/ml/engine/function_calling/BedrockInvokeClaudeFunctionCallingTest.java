/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_FINISH_REASON_TOOL_USE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_INPUT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALLS_TOOL_NAME;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID_PATH;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_QUESTION_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.CHAT_HISTORY_RESPONSE_TEMPLATE;
import static org.opensearch.ml.engine.algorithms.agent.MLChatAgentRunner.INTERACTION_TEMPLATE_TOOL_RESPONSE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;

public class BedrockInvokeClaudeFunctionCallingTest {

    private BedrockInvokeClaudeFunctionCalling functionCalling;
    private Map<String, String> parameters;

    @Before
    public void setUp() {
        functionCalling = new BedrockInvokeClaudeFunctionCalling();
        parameters = new HashMap<>();
    }

    @Test
    public void testConfigure() {
        functionCalling.configure(parameters);

        // Verify all expected parameters are set
        assertNotNull(parameters.get(LLM_RESPONSE_FILTER));
        assertEquals("$.output.message.content[?(@.type=='text')][0].text", parameters.get(LLM_RESPONSE_FILTER));

        assertNotNull(parameters.get(TOOL_TEMPLATE));
        assertTrue(parameters.get(TOOL_TEMPLATE).contains("${tool.name}"));

        assertEquals("$.output.message.content[?(@.type=='tool_use')]", parameters.get(TOOL_CALLS_PATH));
        assertEquals("name", parameters.get(TOOL_CALLS_TOOL_NAME));
        assertEquals("input", parameters.get(TOOL_CALLS_TOOL_INPUT));
        assertEquals("id", parameters.get(TOOL_CALL_ID_PATH));

        assertNotNull(parameters.get("tool_configs"));
        assertTrue(parameters.get("tool_configs").contains("tools"));

        assertEquals("$.output.message", parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_PATH));
        assertNotNull(parameters.get(INTERACTION_TEMPLATE_ASSISTANT_TOOL_CALLS_EXCLUDE_PATH));

        assertNotNull(parameters.get(INTERACTION_TEMPLATE_TOOL_RESPONSE));
        assertNotNull(parameters.get(CHAT_HISTORY_QUESTION_TEMPLATE));
        assertNotNull(parameters.get(CHAT_HISTORY_RESPONSE_TEMPLATE));

        assertEquals("$.stopReason", parameters.get(LLM_FINISH_REASON_PATH));
        assertEquals("tool_use", parameters.get(LLM_FINISH_REASON_TOOL_USE));
    }

    @Test
    public void testHandleWrappedFormatWithToolUse() {
        functionCalling.configure(parameters);

        // Create wrapped format response (streaming format)
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> toolUse = new HashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_123");
        toolUse.put("name", "get_weather");
        toolUse.put("input", Map.of("location", "Seattle"));
        content.add(toolUse);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("get_weather", result.get(0).get("tool_name"));
        assertEquals("{\"location\":\"Seattle\"}", result.get(0).get("tool_input"));
        assertEquals("toolu_123", result.get(0).get("tool_call_id"));
    }

    @Test
    public void testHandleFlatFormatWithToolUse() {
        functionCalling.configure(parameters);

        // Create flat format response (non-streaming format)
        Map<String, Object> dataAsMap = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> toolUse = new HashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_456");
        toolUse.put("name", "search");
        toolUse.put("input", Map.of("query", "OpenSearch"));
        content.add(toolUse);

        dataAsMap.put("role", "assistant");
        dataAsMap.put("content", content);
        dataAsMap.put("stop_reason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("search", result.get(0).get("tool_name"));
        assertEquals("{\"query\":\"OpenSearch\"}", result.get(0).get("tool_input"));
        assertEquals("toolu_456", result.get(0).get("tool_call_id"));
    }

    @Test
    public void testHandleMultipleToolCalls() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> toolUse1 = new HashMap<>();
        toolUse1.put("type", "tool_use");
        toolUse1.put("id", "toolu_1");
        toolUse1.put("name", "tool_a");
        toolUse1.put("input", Map.of("param", "value1"));
        content.add(toolUse1);

        Map<String, Object> toolUse2 = new HashMap<>();
        toolUse2.put("type", "tool_use");
        toolUse2.put("id", "toolu_2");
        toolUse2.put("name", "tool_b");
        toolUse2.put("input", Map.of("param", "value2"));
        content.add(toolUse2);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("tool_a", result.get(0).get("tool_name"));
        assertEquals("tool_b", result.get(1).get("tool_name"));
    }

    @Test
    public void testHandleNoToolUse() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "Here is the answer");
        content.add(text);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "end_turn");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testHandleEmptyToolCalls() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        message.put("content", new ArrayList<>());
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testSupplyWithToolResults() {
        List<Map<String, Object>> toolResults = new ArrayList<>();

        Map<String, Object> result1 = new HashMap<>();
        result1.put("tool_call_id", "toolu_123");
        result1.put("tool_result", "Result 1");
        toolResults.add(result1);

        Map<String, Object> result2 = new HashMap<>();
        result2.put("tool_call_id", "toolu_456");
        result2.put("tool_result", "Result 2");
        toolResults.add(result2);

        List<LLMMessage> messages = functionCalling.supply(toolResults);

        assertNotNull(messages);
        assertEquals(1, messages.size());
        String response = messages.get(0).getResponse();
        assertNotNull(response);
        assertTrue(response.contains("tool_result"));
        assertTrue(response.contains("toolu_123"));
        assertTrue(response.contains("toolu_456"));
    }

    @Test
    public void testSupplyWithToolError() {
        List<Map<String, Object>> toolResults = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("tool_call_id", "toolu_123");
        result.put("tool_result", "Error occurred");
        result.put("tool_error", true);
        toolResults.add(result);

        List<LLMMessage> messages = functionCalling.supply(toolResults);

        assertNotNull(messages);
        assertEquals(1, messages.size());
        String response = messages.get(0).getResponse();
        assertNotNull(response);
        assertTrue(response.contains("tool_result"));
        assertTrue(response.contains("toolu_123"));
    }

    @Test
    public void testSupplyWithMissingToolCallId() {
        List<Map<String, Object>> toolResults = new ArrayList<>();

        Map<String, Object> result = new HashMap<>();
        result.put("tool_result", "Result without ID");
        toolResults.add(result);

        List<LLMMessage> messages = functionCalling.supply(toolResults);

        assertNotNull(messages);
        assertEquals(1, messages.size());
        // Should skip results without tool_call_id
        String response = messages.get(0).getResponse();
        assertTrue(response.contains("\"content\":[]"));
    }

    @Test
    public void testFilterToFirstToolCallWithMultipleTools() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "Let me help");
        content.add(text);

        Map<String, Object> toolUse1 = new HashMap<>();
        toolUse1.put("type", "tool_use");
        toolUse1.put("id", "toolu_1");
        toolUse1.put("name", "first_tool");
        content.add(toolUse1);

        Map<String, Object> toolUse2 = new HashMap<>();
        toolUse2.put("type", "tool_use");
        toolUse2.put("id", "toolu_2");
        toolUse2.put("name", "second_tool");
        content.add(toolUse2);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        Map<String, ?> filtered = functionCalling.filterToFirstToolCall(dataAsMap, parameters);

        assertNotNull(filtered);
        // Should keep text and first tool_use only
        List<?> filteredContent = (List<?>) ((Map<?, ?>) ((Map<?, ?>) filtered.get("output")).get("message")).get("content");
        assertEquals(2, filteredContent.size()); // text + first tool_use
    }

    @Test
    public void testFilterToFirstToolCallWithFlatFormat() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> toolUse1 = new HashMap<>();
        toolUse1.put("type", "tool_use");
        toolUse1.put("name", "first_tool");
        content.add(toolUse1);

        Map<String, Object> toolUse2 = new HashMap<>();
        toolUse2.put("type", "tool_use");
        toolUse2.put("name", "second_tool");
        content.add(toolUse2);

        dataAsMap.put("role", "assistant");
        dataAsMap.put("content", content);
        dataAsMap.put("stop_reason", "tool_use");

        Map<String, ?> filtered = functionCalling.filterToFirstToolCall(dataAsMap, parameters);

        assertNotNull(filtered);
        // Should be wrapped and filtered
        List<?> filteredContent = (List<?>) ((Map<?, ?>) ((Map<?, ?>) filtered.get("output")).get("message")).get("content");
        assertEquals(1, filteredContent.size()); // first tool_use only
    }

    @Test
    public void testFilterToFirstToolCallWithSingleTool() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> toolUse = new HashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("name", "only_tool");
        content.add(toolUse);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));

        Map<String, ?> filtered = functionCalling.filterToFirstToolCall(dataAsMap, parameters);

        assertNotNull(filtered);
        // Should return unchanged
        assertEquals(dataAsMap, filtered);
    }

    @Test
    public void testFilterToFirstToolCallWithNoTools() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "No tools");
        content.add(text);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));

        Map<String, ?> filtered = functionCalling.filterToFirstToolCall(dataAsMap, parameters);

        assertNotNull(filtered);
        // Should return unchanged
        assertEquals(dataAsMap, filtered);
    }

    @Test
    public void testHandleWithCompactionBlock() {
        functionCalling.configure(parameters);

        // Create response with compaction and tool_use
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> compaction = new HashMap<>();
        compaction.put("type", "compaction");
        compaction.put("data", "compressed_data");
        content.add(compaction);

        Map<String, Object> toolUse = new HashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_789");
        toolUse.put("name", "summarize");
        toolUse.put("input", Map.of("text", "content"));
        content.add(toolUse);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("summarize", result.get(0).get("tool_name"));
    }

    @Test
    public void testClaudeMessageGetResponse() {
        BedrockInvokeClaudeFunctionCalling.ClaudeMessage message = new BedrockInvokeClaudeFunctionCalling.ClaudeMessage();
        message.setRole("user");

        BedrockInvokeClaudeFunctionCalling.ToolResult toolResult = new BedrockInvokeClaudeFunctionCalling.ToolResult();
        toolResult.setType("tool_result");
        toolResult.setToolUseId("toolu_123");
        toolResult.setContent("Result content");

        message.getContent().add(toolResult);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"user\""));
        assertTrue(response.contains("tool_result"));
        assertTrue(response.contains("toolu_123"));
    }

    @Test
    public void testClaudeMessageWithNullFields() {
        BedrockInvokeClaudeFunctionCalling.ClaudeMessage message = new BedrockInvokeClaudeFunctionCalling.ClaudeMessage();
        message.setRole(null);
        message.setContent(null);

        String response = message.getResponse();

        assertNotNull(response);
        assertTrue(response.contains("\"role\":\"user\"")); // Should default to "user"
        assertTrue(response.contains("\"content\":[]")); // Should default to empty list
    }

    @Test
    public void testToolResultWithError() {
        BedrockInvokeClaudeFunctionCalling.ToolResult toolResult = new BedrockInvokeClaudeFunctionCalling.ToolResult();
        toolResult.setType("tool_result");
        toolResult.setToolUseId("toolu_error");
        toolResult.setContent("Error message");
        toolResult.setError(true);

        assertTrue(toolResult.isError());
        assertEquals("toolu_error", toolResult.getToolUseId());
        assertEquals("Error message", toolResult.getContent());
    }

    @Test
    public void testHandleWithTextAndToolUse() {
        functionCalling.configure(parameters);

        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Map<String, Object>> content = new ArrayList<>();

        // Add text block before tool_use
        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", "I'll help you with that.");
        content.add(text);

        Map<String, Object> toolUse = new HashMap<>();
        toolUse.put("type", "tool_use");
        toolUse.put("id", "toolu_mixed");
        toolUse.put("name", "calculator");
        toolUse.put("input", Map.of("expression", "2+2"));
        content.add(toolUse);

        message.put("role", "assistant");
        message.put("content", content);
        dataAsMap.put("output", Map.of("message", message));
        dataAsMap.put("stopReason", "tool_use");

        ModelTensorOutput modelTensorOutput = createModelTensorOutput(dataAsMap);

        List<Map<String, String>> result = functionCalling.handle(modelTensorOutput, parameters);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("calculator", result.get(0).get("tool_name"));
        assertEquals("{\"expression\":\"2+2\"}", result.get(0).get("tool_input"));
    }

    // Helper method to create ModelTensorOutput
    private ModelTensorOutput createModelTensorOutput(Map<String, Object> dataAsMap) {
        return ModelTensorOutput
            .builder()
            .mlModelOutputs(
                List
                    .of(
                        ModelTensors
                            .builder()
                            .mlModelTensors(List.of(ModelTensor.builder().name("response").dataAsMap(dataAsMap).build()))
                            .build()
                    )
            )
            .build();
    }
}
