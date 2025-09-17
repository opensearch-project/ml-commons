/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.opensearch.ml.engine.tools.MLModelTool.DEFAULT_DESCRIPTION;
import static org.opensearch.ml.engine.tools.MLModelTool.MODEL_ID_FIELD;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.spi.tools.Parser;
import org.opensearch.ml.common.spi.tools.Tool;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.repackage.com.google.common.collect.ImmutableMap;
import org.opensearch.transport.client.Client;

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
    public void testMLModelsWithDefaultOutputParserAndDefaultResponseField() throws ExecutionException, InterruptedException {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {

            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);

            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "modelId"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(null, listener);

        future.join();
        assertEquals("response 1", future.get());
    }

    @Test
    public void testMLModelsWithDefaultOutputParserAndCustomizedResponseField() throws ExecutionException, InterruptedException {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {

            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);

            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "modelId", "response_field", "action"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(null, listener);

        future.join();
        assertEquals("action1", future.get());
    }

    @Test
    public void testMLModelsWithDefaultOutputParserAndMalformedResponseField() throws ExecutionException, InterruptedException {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "response 1", "action", "action1")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        doAnswer(invocation -> {

            ActionListener<MLTaskResponse> actionListener = invocation.getArgument(2);

            actionListener.onResponse(MLTaskResponse.builder().output(mlModelTensorOutput).build());
            return null;
        }).when(client).execute(eq(MLPredictionTaskAction.INSTANCE), any(), any());

        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "modelId", "response_field", "malformed field"));
        final CompletableFuture<String> future = new CompletableFuture<>();
        ActionListener<String> listener = ActionListener.wrap(r -> { future.complete(r); }, e -> { future.completeExceptionally(e); });
        tool.run(null, listener);

        future.join();
        assertEquals("{\"response\":\"response 1\",\"action\":\"action1\"}", future.get());
    }

    @Test
    public void testMLModelsWithCustomizedOutputParser() {
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("thought", "thought 1", "action", "action1")).build();
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
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "testResponse", "action", "action1")).build();
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
    public void testOutputParserWithJsonResponse() {
        Parser outputParser = new MLModelTool(client, "modelId", "response").getOutputParser();
        String expectedJson = "{\"key1\":\"value1\",\"key2\":\"value2\"}";

        // Create a mock ModelTensors with json object
        ModelTensor modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("key1", "value1", "key2", "value2")).build();
        ModelTensors modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        ModelTensorOutput mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        Object result = outputParser.parse(mlModelTensorOutput.getMlModelOutputs());
        assertEquals(expectedJson, result);

        // Create a mock ModelTensors with response string
        modelTensor = ModelTensor.builder().dataAsMap(ImmutableMap.of("response", "{\"key1\":\"value1\",\"key2\":\"value2\"}")).build();
        modelTensors = ModelTensors.builder().mlModelTensors(Arrays.asList(modelTensor)).build();
        mlModelTensorOutput = ModelTensorOutput.builder().mlModelOutputs(Arrays.asList(modelTensors)).build();
        result = outputParser.parse(mlModelTensorOutput.getMlModelOutputs());
        assertEquals(expectedJson, result);
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
        Tool tool = MLModelTool.Factory.getInstance().create(Map.of("model_id", "test_model_id"));
        assertEquals(MLModelTool.TYPE, tool.getName());
        assertEquals(MLModelTool.TYPE, tool.getType());
        assertNull(tool.getVersion());
        assertTrue(tool.validate(indicesParams));
        assertTrue(tool.validate(otherParams));
        assertFalse(tool.validate(emptyParams));
        assertEquals(DEFAULT_DESCRIPTION, tool.getDescription());
        assertEquals(List.of(MODEL_ID_FIELD), MLModelTool.Factory.getInstance().getAllModelKeys());
    }

    @Test
    public void testToolWithFailure() {
        assertThrows(IllegalArgumentException.class, () -> MLModelTool.Factory.getInstance().create(Collections.emptyMap()));
    }

    @Test
    public void testToolWithNullModelId() {
        assertThrows(IllegalArgumentException.class, () -> new MLModelTool(client, null, "response"));
    }

    @Test
    public void testToolWithBlankModelId() {
        assertThrows(IllegalArgumentException.class, () -> new MLModelTool(client, "", "response"));
    }
}
