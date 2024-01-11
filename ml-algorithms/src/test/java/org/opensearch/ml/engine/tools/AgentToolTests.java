/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.engine.tools.AgentTool.DEFAULT_DESCRIPTION;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
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
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;

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
        AgentMLInput agentMLInput = AgentMLInput
            .AgentMLInputBuilder()
            .agentId("agentId")
            .functionName(FunctionName.AGENT)
            .inputDataset(RemoteInferenceInputDataSet.builder().parameters(parameters).build())
            .build();

        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(Map.of("thought", "thought 1", "action", "action1")).build();
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
        Tool tool = AgentTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(AgentTool.TYPE, tool.getName());
        assertEquals(AgentTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertTrue(tool.validate(emptyParams));
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
    }
}
