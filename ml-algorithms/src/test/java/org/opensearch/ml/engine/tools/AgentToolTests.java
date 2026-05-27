/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.common.utils.StringUtils.gson;
import static org.opensearch.ml.engine.tools.AgentTool.AGENT_CALL_DEPTH_FIELD;
import static org.opensearch.ml.engine.tools.AgentTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.AgentTool.MAX_AGENT_CALL_DEPTH;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskAction;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

public class AgentToolTests {

    @Mock
    private Client client;
    private Map<String, String> indicesParams;
    private Map<String, String> otherParams;
    private Map<String, String> emptyParams;
    @Mock
    private Parser mockOutputParser;

    @Mock
    private MLExecuteTaskResponse mockResponse;

    @Mock
    private ActionListener<ModelTensorOutput> listener;

    private AgentTool agentTool;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        AgentTool.Factory.getInstance().init(client);

        indicesParams = Map.of("index", "[\"foo\"]");
        otherParams = Map.of("other", "[\"bar\"]");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testAgenttestRunMethod() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");
        doTestRunMethod(parameters);
    }

    @Test
    public void testAgentWithChatAgentInput() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");
        Map<String, String> chatAgentInput = new HashMap<>();
        chatAgentInput.put("input", gson.toJson(parameters));
        doTestRunMethod(chatAgentInput);
        assertEquals(chatAgentInput.size(), 1);
        assertEquals(chatAgentInput.get("input"), gson.toJson(parameters)); // assert no influence on original parameters
    }

    @Test
    public void testAgentWithChatAgentInputWrongFormat() {
        Map<String, String> chatAgentInput = new HashMap<>();
        chatAgentInput.put("input", "wrong format");
        doTestRunMethod(chatAgentInput);
    }

    private void doTestRunMethod(Map<String, String> parameters) {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("thought", "thought 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "modelId"));

        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), any(), any());

        tool.run(parameters, listener);

        // Verify interactions
        verify(client).execute(any(), any(), any());
        verify(listener).onResponse(mlModelTensorOutput);
    }

    @Test
    public void testRunWithError() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");

        // Mocking the client.execute to simulate an error
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Test Exception"));
            return null;
        }).when(client).execute(any(), any(), any());

        // Running the test
        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "modelId"));
        tool.run(parameters, listener);

        // Verifying that onFailure was called
        verify(listener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testTool() {
        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "test_agent_id"));
        assertEquals(AgentTool.TYPE, tool.getName());
        assertEquals(AgentTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertTrue(tool.validate(emptyParams));
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
    }

    @Test
    public void testToolFailure() {
        assertThrows(IllegalArgumentException.class, () -> AgentTool.Factory.getInstance().create(Collections.emptyMap()));
        assertThrows(IllegalArgumentException.class, () -> AgentTool.Factory.getInstance().create(Map.of("agent_id", "")));
    }

    @Test
    public void testRunWithNullAgentId() {
        AgentTool tool = new AgentTool(client, "test_agent_id");
        tool.setAgentId(null);

        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");

        tool.run(parameters, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testRunWithBlankAgentId() {
        AgentTool tool = new AgentTool(client, "test_agent_id");
        tool.setAgentId("");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");

        tool.run(parameters, listener);

        verify(listener).onFailure(any(IllegalArgumentException.class));
    }

    @Test
    public void testRunWithGeneralException() {
        AgentTool tool = new AgentTool(null, "test_agent_id");

        Map<String, String> parameters = new HashMap<>();
        parameters.put("testKey", "testValue");

        tool.run(parameters, listener);

        verify(listener).onFailure(any(Exception.class));
    }

    @Test
    public void testRunStampsInitialCallDepth() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("k", "v")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput out = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "child"));

        ArgumentCaptor<MLExecuteTaskRequest> captor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> al = invocation.getArgument(2);
            al.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(out).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), captor.capture(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "q");
        tool.run(parameters, listener);

        AgentMLInput input = (AgentMLInput) captor.getValue().getInput();
        Map<String, String> innerParams = ((RemoteInferenceInputDataSet) input.getInputDataset()).getParameters();
        assertEquals("1", innerParams.get(AGENT_CALL_DEPTH_FIELD));
        verify(listener).onResponse(out);
    }

    @Test
    public void testRunBlocksWhenNestedAgentTriesToCallAnotherAgent() {
        // The first nested agent (depth=1) must not invoke another AgentTool.
        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "child"));

        Map<String, String> parameters = new HashMap<>();
        parameters.put(AGENT_CALL_DEPTH_FIELD, String.valueOf(MAX_AGENT_CALL_DEPTH));
        tool.run(parameters, listener);

        verify(client, never()).execute(any(), any(), any());
        verify(listener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testRunBlocksWhenDepthExceedsMax() {
        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "child"));

        Map<String, String> parameters = new HashMap<>();
        parameters.put(AGENT_CALL_DEPTH_FIELD, String.valueOf(MAX_AGENT_CALL_DEPTH + 1));
        tool.run(parameters, listener);

        verify(client, never()).execute(any(), any(), any());
        verify(listener).onFailure(any(IllegalStateException.class));
    }

    @Test
    public void testRunIgnoresGarbageDepthValue() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("k", "v")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput out = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "child"));

        ArgumentCaptor<MLExecuteTaskRequest> captor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> al = invocation.getArgument(2);
            al.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(out).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), captor.capture(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put(AGENT_CALL_DEPTH_FIELD, "not-a-number");
        tool.run(parameters, listener);

        // Garbage value is treated as depth 0 and the chain is allowed to start at 1.
        AgentMLInput input = (AgentMLInput) captor.getValue().getInput();
        Map<String, String> innerParams = ((RemoteInferenceInputDataSet) input.getInputDataset()).getParameters();
        assertEquals("1", innerParams.get(AGENT_CALL_DEPTH_FIELD));
    }

    @Test
    public void testAgentIdLNotGetOverride() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("thought", "thought 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        Tool tool = AgentTool.Factory.getInstance().create(Map.of("agent_id", "configured-sub-agent-id"));

        ArgumentCaptor<MLExecuteTaskRequest> requestCaptor = ArgumentCaptor.forClass(MLExecuteTaskRequest.class);
        doAnswer(invocation -> {
            ActionListener<MLExecuteTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onResponse(MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLExecuteTaskAction.INSTANCE), requestCaptor.capture(), any());

        Map<String, String> parameters = new HashMap<>();
        parameters.put("question", "test question");

        tool.run(parameters, listener);

        AgentMLInput capturedInput = (AgentMLInput) requestCaptor.getValue().getInput();
        assertEquals("configured-sub-agent-id", capturedInput.getAgentId());
        verify(listener).onResponse(mlModelTensorOutput);
    }
}
