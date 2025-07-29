/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.function_calling;

import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.LLM_RESPONSE_FILTER;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_CALL_ID;
import static org.opensearch.ml.engine.algorithms.agent.AgentUtils.TOOL_RESULT;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.BEDROCK_DEEPSEEK_R1_TOOL_TEMPLATE;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.CALL_PATH;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.FINISH_REASON;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.FINISH_REASON_PATH;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.ID_PATH;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.INPUT;
import static org.opensearch.ml.engine.function_calling.BedrockConverseDeepseekR1FunctionCalling.NAME;

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
import org.opensearch.ml.common.utils.StringUtils;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;

public class BedrockConverseDeepseekR1FunctionCallingTests {

    FunctionCalling functionCalling;
    ModelTensorOutput mlModelTensorOutput;
    List<Map<String, String>> tools;

    @Before
    public void setUp() {
        functionCalling = FunctionCallingFactory.create(LLM_INTERFACE_BEDROCK_CONVERSE_DEEPSEEK_R1);
        tools = List.of(ImmutableMap.of(NAME, "test_tool_name", INPUT, "test_tool_input", ID_PATH, "test_tool_call_id"));
        ModelTensor modelTensor = ModelTensor
            .builder()
            .dataAsMap(ImmutableMap.of(FINISH_REASON_PATH, FINISH_REASON, CALL_PATH, tools))
            .build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
    }

    @Test
    public void configure() {
        Map<String, String> parameters = new HashMap<>();
        functionCalling.configure(parameters);
        Assert.assertEquals(15, parameters.size());
        Assert.assertEquals(BEDROCK_DEEPSEEK_R1_TOOL_TEMPLATE, parameters.get("tool_template"));
    }

    @Test
    public void handle() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put(LLM_RESPONSE_FILTER, "$");
        List<Map<String, String>> toolCalls = functionCalling.handle(mlModelTensorOutput, parameters);
        Assert.assertEquals(tools.size(), toolCalls.size());
        for (int i = 0; i < tools.size(); ++i) {
            Assert.assertEquals(tools.get(i).get(NAME), toolCalls.get(i).get("tool_name"));
            Assert.assertEquals(tools.get(i).get(INPUT), toolCalls.get(i).get("tool_input"));
            Assert.assertEquals(tools.get(i).get(ID_PATH), toolCalls.get(i).get("tool_call_id"));
        }
    }

    @Test
    public void supply() {
        List<LLMMessage> messages = functionCalling
            .supply(List.of(ImmutableMap.of(TOOL_CALL_ID, "test_tool_call_id", TOOL_RESULT, "test result for bedrock deepseek")));
        Assert.assertEquals(1, messages.size());
        LLMMessage message = messages.get(0);
        Assert.assertEquals("user", message.getRole());
        List<Object> content = (List<Object>) message.getContent();
        Map<String, String> textMap = (Map<String, String>) content.get(0);
        String textJson = textMap.get("text");
        Map<String, Object> resultMap = StringUtils.fromJson(textJson, "response");
        Assert.assertEquals("test_tool_call_id", resultMap.get("tool_call_id"));
        Assert.assertEquals("test result for bedrock deepseek", resultMap.get("tool_result"));
    }
}
