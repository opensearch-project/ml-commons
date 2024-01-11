/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.opensearch.ml.engine.tools.MLModelTool.DEFAULT_DESCRIPTION;

import java.util.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.client.Client;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;

public class MLModelToolTests {

    @Mock
    private Client client;
    private Map<String, String> indicesParams;
    private Map<String, String> otherParams;
    private Map<String, String> emptyParams;
    @Mock
    private Parser mockOutputParser;

    @Mock
    private ActionListener<ModelTensorOutput> listener;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        MLModelTool.Factory.getInstance().init(client);

        indicesParams = Map.of("index", "[\"foo\"]");
        otherParams = Map.of("other", "[\"bar\"]");
        emptyParams = Collections.emptyMap();
    }

    @Test
    public void testMLModelsWithOutputParser() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(Map.of("thought", "thought 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {

            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);

            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        tool.setOutputParser(mockOutputParser);
        tool.run(otherParams, listener);

        verify(client).execute(any(), any(), any());
        verify(mockOutputParser).parse(any());
        ArgumentCaptor<ModelTensorOutput> dataFrameArgumentCaptor = ArgumentCaptor.forClass(ModelTensorOutput.class);
        verify(listener).onResponse(dataFrameArgumentCaptor.capture());
    }

    @Test
    public void testOutputParserLambda() {
        // Create a mock ModelTensors object
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(Map.of("response", "testResponse", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();

        // Create the lambda expression for outputParser
        Parser outputParser = o -> {
            List<ModelTensors> outputs = (List<ModelTensors>) o;
            return outputs.get(0).getMlModelTensors().get(0).getDataAsMap().get("response");
        };

        // Invoke the lambda with the mock data
        Object result = outputParser.parse(mlModelTensorOutput.getMlModelOutputs());

        // Assert that the result matches the expected response
        assertEquals("testResponse", result);
    }

    @Test
    public void testRunWithError() {
        // Mocking the client.execute to simulate an error
        doAnswer(invocation -> {
            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);
            actionListener.onFailure(new RuntimeException("Test Exception"));
            return null;
        }).when(client).execute(any(), any(), any());

        // Running the test
        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        tool.setOutputParser(mockOutputParser);
        tool.run(otherParams, listener);

        // Verifying that onFailure was called
        verify(listener).onFailure(any(RuntimeException.class));
    }

    @Test
    public void testTool() {
        Tool tool = MLModelTool.Factory.getInstance().create(Collections.emptyMap());
        assertEquals(MLModelTool.TYPE, tool.getName());
        assertEquals(MLModelTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
    }
}
