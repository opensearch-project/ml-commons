/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.junit.Assert.*;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.function_calling.GeminiV1BetaGenerateContentFunctionCalling.GEMINI_TOOL_TEMPLATE;
import static org.opensearch.ml.engine.function_calling.GeminiV1BetaGenerateContentFunctionCalling.INPUT;
import static org.opensearch.ml.engine.function_calling.GeminiV1BetaGenerateContentFunctionCalling.NAME;

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

public class GeminiV1BetaGenerateContentFunctionCallingTests {

    FunctionCalling functionCalling;
    ModelTensorOutput mlModelTensorOutput;
    Map<String, Object> tool;

    @Before
    public void setUp() {
        functionCalling = FunctionCallingFactory.create(LLM_INTERFACE_GEMINI_V1BETA_GENERATE_CONTENT);
        tool = ImmutableMap.of(NAME, "test_tool_name", INPUT, "test_tool_input");
        List<Object> parts = List.of(ImmutableMap.of("functionCall", tool));
        Map<String, Object> content = ImmutableMap.of("role", "model", "parts", parts);
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of("candidates", Arrays.asList(ImmutableMap.of("content", content, "finishReason", "STOP"))))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
    }

    @Test
    public void configure() {
        Map<String, String> parameters = new HashMap<>();
        functionCalling.configure(parameters);
        Assert.assertEquals(15, parameters.size());
        Assert.assertEquals(GEMINI_TOOL_TEMPLATE, parameters.get("tool_template"));
    }

    @Test
    public void handle() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$");
        List<Map<String, String>> toolCalls = functionCalling.handle(mlModelTensorOutput, parameters);
        Assert.assertEquals(1, toolCalls.size());
        Assert.assertEquals(tool.get(NAME), toolCalls.get(0).get("tool_name"));
        Assert.assertEquals(tool.get(INPUT), toolCalls.get(0).get("tool_input"));
        // In Gemini, tool_call_id is the same as tool_name (both use "name" field)
        Assert.assertEquals(tool.get(NAME), toolCalls.get(0).get("tool_call_id"));
    }

    @Test
    public void supply() {
        Map<String, Object> toolResultMap = ImmutableMap.of("text", "test result for gemini generateContent");
        List<LLMMessage> messages = functionCalling
            .supply(List.of(ImmutableMap.of(TOOL_CALL_ID, "test_tool_call_id", TOOL_RESULT, toolResultMap)));
        Assert.assertEquals(1, messages.size());
        GeminiMessage message = (GeminiMessage) messages.get(0);
        Assert.assertEquals("user", message.getRole());
        Assert.assertEquals(1, message.getContent().size());
        Map<String, Object> contentMap = (Map<String, Object>) message.getContent().get(0);
        Map<String, Object> functionResponse = (Map<String, Object>) contentMap.get("functionResponse");
        Assert.assertEquals("test_tool_call_id", functionResponse.get("name"));
        Assert.assertEquals(toolResultMap, functionResponse.get("response"));
    }

    @Test
    public void filterToFirstToolCall_multipleToolCalls() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> candidates = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        List<Object> parts = Arrays
            .asList(
                ImmutableMap.of("text", "Some text"),
                ImmutableMap.of("functionCall", ImmutableMap.of("name", "tool1")),
                ImmutableMap.of("functionCall", ImmutableMap.of("name", "tool2"))
            );
        content.put("parts", parts);
        candidates.put("content", content);
        dataAsMap.put("candidates", Arrays.asList(candidates));

        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultParts = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("candidates")).get(0)).get("content"))
            .get("parts");

        assertEquals(2, resultParts.size());
        assertEquals("Some text", ((Map<?, ?>) resultParts.get(0)).get("text"));
        assertEquals("tool1", ((Map<?, ?>) ((Map<?, ?>) resultParts.get(1)).get("functionCall")).get("name"));
    }

    @Test
    public void filterToFirstToolCall_singleToolCall() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> candidates = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        List<Object> parts = Arrays.asList(ImmutableMap.of("functionCall", ImmutableMap.of("name", "tool1")));
        content.put("parts", parts);
        candidates.put("content", content);
        dataAsMap.put("candidates", Arrays.asList(candidates));

        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultParts = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("candidates")).get(0)).get("content"))
            .get("parts");
        assertEquals(1, resultParts.size());
    }

    @Test
    public void filterToFirstToolCall_noToolCalls() {
        Map<String, Object> dataAsMap = new HashMap<>();
        Map<String, Object> candidates = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        List<Object> parts = Arrays.asList(ImmutableMap.of("text", "Just text"));
        content.put("parts", parts);
        candidates.put("content", content);
        dataAsMap.put("candidates", Arrays.asList(candidates));

        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultParts = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("candidates")).get(0)).get("content"))
            .get("parts");
        assertEquals(1, resultParts.size());
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
        Map<String, Object> dataAsMap = ImmutableMap
            .of(
                "candidates",
                Arrays
                    .asList(
                        ImmutableMap
                            .of(
                                "content",
                                ImmutableMap
                                    .of(
                                        "parts",
                                        Arrays
                                            .asList(
                                                ImmutableMap.of("text", "Some text"),
                                                ImmutableMap.of("functionCall", ImmutableMap.of("name", "tool1")),
                                                ImmutableMap.of("functionCall", ImmutableMap.of("name", "tool2"))
                                            )
                                    )
                            )
                    )
            );

        Map<String, ?> result = functionCalling.filterToFirstToolCall(dataAsMap, new HashMap<>());
        List<Object> resultParts = (List<Object>) ((Map<?, ?>) ((Map<?, ?>) ((List<?>) result.get("candidates")).get(0)).get("content"))
            .get("parts");

        assertEquals(2, resultParts.size());
        assertEquals("Some text", ((Map<?, ?>) resultParts.get(0)).get("text"));
        assertEquals("tool1", ((Map<?, ?>) ((Map<?, ?>) resultParts.get(1)).get("functionCall")).get("name"));
    }
}
