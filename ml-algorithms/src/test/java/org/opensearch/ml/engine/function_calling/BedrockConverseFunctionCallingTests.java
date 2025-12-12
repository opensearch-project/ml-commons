/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.function_calling.BedrockConverseFunctionCalling.BEDROCK_CONVERSE_TOOL_TEMPLATE;
import static org.opensearch.ml.engine.function_calling.BedrockConverseFunctionCalling.ID_PATH;
import static org.opensearch.ml.engine.function_calling.BedrockConverseFunctionCalling.INPUT;
import static org.opensearch.ml.engine.function_calling.BedrockConverseFunctionCalling.NAME;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;

public class BedrockConverseFunctionCallingTests {

    FunctionCalling functionCalling;
    ModelTensorOutput mlModelTensorOutput;
    Map<String, String> tool;

    @Before
    public void setUp() {
        functionCalling = FunctionCallingFactory.create(LLM_INTERFACE_BEDROCK_CONVERSE_CLAUDE);
        tool = ImmutableMap.of(NAME, "test_tool_name", INPUT, "test_tool_input", ID_PATH, "test_tool_call_id");
        List<Object> messageContent = List.of(ImmutableMap.of("toolUse", tool));
        Map<String, Object> message = ImmutableMap.of("role", "assistant", "content", messageContent);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("output", ImmutableMap.of("message", message), "stopReason", "tool_use"))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
    }

    @Test
    public void configure() {
        Map<String, String> parameters = new HashMap<>();
        functionCalling.configure(parameters);
        Assert.assertEquals(14, parameters.size());
        Assert.assertEquals(BEDROCK_CONVERSE_TOOL_TEMPLATE, parameters.get("tool_template"));
    }

    @Test
    public void handle() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$");
        List<Map<String, String>> toolCalls = functionCalling.handle(mlModelTensorOutput, parameters);
        Assert.assertEquals(1, toolCalls.size());
        Assert.assertEquals(tool.get(NAME), toolCalls.get(0).get("tool_name"));
        Assert.assertEquals(tool.get(INPUT), toolCalls.get(0).get("tool_input"));
        Assert.assertEquals(tool.get(ID_PATH), toolCalls.get(0).get("tool_call_id"));
    }

    @Test
    public void supply() {
        List<LLMMessage> messages = functionCalling
            .supply(List.of(ImmutableMap.of(TOOL_CALL_ID, "test_tool_call_id", TOOL_RESULT, "test result for bedrock converse")));
        Assert.assertEquals(1, messages.size());
        BedrockMessage message = (BedrockMessage) messages.get(0);
        Assert.assertEquals("user", message.getRole());
        Assert.assertEquals(1, message.getContent().size());
        Map<String, BedrockConverseFunctionCalling.ToolResult> contentMap = (Map<String, BedrockConverseFunctionCalling.ToolResult>) message
            .getContent()
            .get(0);
        BedrockConverseFunctionCalling.ToolResult result = contentMap.get("toolResult");
        Assert.assertEquals("test_tool_call_id", result.getToolUseId());
        Assert.assertEquals("test result for bedrock converse", result.getContent().get(0));
    }

    @Test
    public void filterToFirstToolCall_multipleToolCalls() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Object> content = Arrays.asList(
            ImmutableMap.of("text", "Some text"),
            ImmutableMap.of("toolUse", ImmutableMap.of("name", "tool1")),
            ImmutableMap.of("toolUse", ImmutableMap.of("name", "tool2"))
        );
        message.put("content", content);
        output.put("message", message);
        dataAsMap.put("output", output);
        
        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultContent = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) result.get("output")).get("message")).get("content");
        
        assertEquals(2, resultContent.size());
        assertEquals("Some text", ((Map<?, ?>) resultContent.get(0)).get("text"));
        assertEquals("tool1", ((Map<?, ?>) ((Map<?, ?>) resultContent.get(1)).get("toolUse")).get("name"));
    }

    @Test
    public void filterToFirstToolCall_singleToolCall() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Object> content = Arrays.asList(
            ImmutableMap.of("toolUse", ImmutableMap.of("name", "tool1"))
        );
        message.put("content", content);
        output.put("message", message);
        dataAsMap.put("output", output);
        
        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultContent = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) result.get("output")).get("message")).get("content");
        assertEquals(1, resultContent.size());
    }

    @Test
    public void filterToFirstToolCall_noToolCalls() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> output = new HashMap<>();
        Map<String, Object> message = new HashMap<>();
        List<Object> content = Arrays.asList(
            ImmutableMap.of("text", "Just text")
        );
        message.put("content", content);
        output.put("message", message);
        dataAsMap.put("output", output);
        
        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultContent = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) result.get("output")).get("message")).get("content");
        assertEquals(1, resultContent.size());
    }

    @Test
    public void filterToFirstToolCall_invalidStructure() {
        Map<String, Object> dataAsMap = new HashMap<>();
        dataAsMap.put("invalid", "structure");
        
        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        assertEquals(dataAsMap, result);
    }

    @Test
    public void filterToFirstToolCall_immutableMap() {
        Map<String, Object> dataAsMap = ImmutableMap.of(
            "output", ImmutableMap.of(
                "message", ImmutableMap.of(
                    "content", Arrays.asList(
                        ImmutableMap.of("text", "Some text"),
                        ImmutableMap.of("toolUse", ImmutableMap.of("name", "tool1")),
                        ImmutableMap.of("toolUse", ImmutableMap.of("name", "tool2"))
                    )
                )
            )
        );
        
        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultContent = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) result.get("output")).get("message")).get("content");
        
        assertEquals(2, resultContent.size());
        assertEquals("Some text", ((Map<?, ?>) resultContent.get(0)).get("text"));
        assertEquals("tool1", ((Map<?, ?>) ((Map<?, ?>) resultContent.get(1)).get("toolUse")).get("name"));
    }
}
