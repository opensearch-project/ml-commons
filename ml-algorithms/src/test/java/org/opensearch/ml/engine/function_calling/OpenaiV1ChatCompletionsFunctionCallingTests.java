/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.function_calling.OpenaiV1ChatCompletionsFunctionCalling.FINISH_REASON;
import static org.opensearch.ml.engine.function_calling.OpenaiV1ChatCompletionsFunctionCalling.OPENAI_V1_CHAT_COMPLETION_TEMPLATE;

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

public class OpenaiV1ChatCompletionsFunctionCallingTests {
    FunctionCalling functionCalling;
    ModelTensorOutput mlModelTensorOutput;
    Map<String, String> tool;

    @Before
    public void setUp() {
        functionCalling = FunctionCallingFactory.create(LLM_INTERFACE_OPENAI_V1_CHAT_COMPLETIONS);
        tool = ImmutableMap.of("name", "test_tool_name", "arguments", "test_tool_input", "id", "test_tool_call_id");
        List<Object> tool_calls = List
            .of(
                ImmutableMap
                    .of(
                        "id",
                        "test_tool_call_id",
                        "type",
                        "function",
                        "function",
                        ImmutableMap.of("name", "test_tool_name", "arguments", "test_tool_input")
                    )
            );
        List<Object> choices = List
            .of(ImmutableMap.of("message", ImmutableMap.of("tool_calls", tool_calls), "finish_reason", FINISH_REASON));
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("choices", choices)).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
    }

    @Test
    public void configure() {
        Map<String, String> parameters = new HashMap<>();
        functionCalling.configure(parameters);
        Assert.assertEquals(16, parameters.size());
        Assert.assertEquals(OPENAI_V1_CHAT_COMPLETION_TEMPLATE, parameters.get("tool_template"));
    }

    @Test
    public void handle() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$");
        List<Map<String, String>> toolCalls = functionCalling.handle(mlModelTensorOutput, parameters);
        Assert.assertEquals(1, toolCalls.size());
        Assert.assertEquals(tool.get("name"), toolCalls.get(0).get("tool_name"));
        Assert.assertEquals(tool.get("arguments"), toolCalls.get(0).get("tool_input"));
        Assert.assertEquals(tool.get("id"), toolCalls.get(0).get("tool_call_id"));
    }

    @Test
    public void supply() {
        List<LLMMessage> messages = functionCalling
            .supply(
                List
                    .of(
                        ImmutableMap
                            .of(TOOL_CALL_ID, "test_tool_call_id", TOOL_RESULT, ImmutableMap.of("text", "test result for openai v1"))
                    )
            );
        Assert.assertEquals(1, messages.size());
        OpenaiMessage message = (OpenaiMessage) messages.get(0);
        Assert.assertEquals("tool", message.getRole());
        Assert.assertEquals("test_tool_call_id", message.getToolCallId());
        Assert.assertEquals("test result for openai v1", message.getContent());
    }

    @Test
    public void supplyParallelToolCalls() {
        // Test parallel tool calls with unique tool_call_id values
        List<LLMMessage> messages = functionCalling
            .supply(
                Arrays
                    .asList(
                        ImmutableMap.of(TOOL_CALL_ID, "tooluse_1", TOOL_RESULT, ImmutableMap.of("text", "result from tool 1")),
                        ImmutableMap.of(TOOL_CALL_ID, "tooluse_2", TOOL_RESULT, ImmutableMap.of("text", "result from tool 2"))
                    )
            );

        Assert.assertEquals(2, messages.size());

        // Verify first message has correct tool_call_id
        OpenaiMessage message1 = (OpenaiMessage) messages.get(0);
        Assert.assertEquals("tool", message1.getRole());
        Assert.assertEquals("tooluse_1", message1.getToolCallId());
        Assert.assertEquals("result from tool 1", message1.getContent());

        // Verify second message has correct tool_call_id (not duplicate of first)
        OpenaiMessage message2 = (OpenaiMessage) messages.get(1);
        Assert.assertEquals("tool", message2.getRole());
        Assert.assertEquals("tooluse_2", message2.getToolCallId());
        Assert.assertEquals("result from tool 2", message2.getContent());

        // Verify they are different objects
        Assert.assertNotSame("Messages should be different objects", message1, message2);

        // Verify tool_call_ids are unique
        Assert.assertNotEquals("tool_call_id values should be unique", message1.getToolCallId(), message2.getToolCallId());
    }
}
