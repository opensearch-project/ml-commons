/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.action.ActionRequest;
import org.opensearch.core.action.ActionListener;
import org.opensearch.ml.common.agent.LLMSpec;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.prediction.MLPredictionStreamTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskAction;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.client.Client;

public class StreamingWrapperTest {

    @Mock
    private TransportChannel channel;

    @Mock
    private Client client;

    @Mock
    private ActionListener<Object> listener;

    @Mock
    private ActionListener<MLTaskResponse> mlTaskListener;

    private StreamingWrapper streamingWrapper;
    private StreamingWrapper nonStreamingWrapper;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        streamingWrapper = new StreamingWrapper(channel, client);
        nonStreamingWrapper = new StreamingWrapper(null, client);
    }

    @Test
    public void testConstructor() {
        StreamingWrapper wrapper = new StreamingWrapper(channel, client);
        assertNotNull(wrapper);
    }

    @Test
    public void testFixInteractionRoleStreaming() {
        List<String> interactions = new ArrayList<>();
        interactions.add("{\"tool_calls\":[{\"name\":\"test\"}]}");

        streamingWrapper.fixInteractionRole(interactions);

        assertTrue(interactions.get(0).contains("\"role\":\"assistant\""));
    }

    @Test
    public void testFixInteractionRoleNonStreaming() {
        List<String> interactions = new ArrayList<>();
        interactions.add("{\"tool_calls\":[{\"name\":\"test\"}]}");

        nonStreamingWrapper.fixInteractionRole(interactions);

        assertFalse(interactions.get(0).contains("\"role\":\"assistant\""));
    }

    @Test
    public void testFixInteractionRoleWithEmptyInteractions() {
        List<String> interactions = new ArrayList<>();

        streamingWrapper.fixInteractionRole(interactions);

        assertTrue(interactions.isEmpty());
    }

    @Test
    public void testFixInteractionRoleWithExistingRole() {
        List<String> interactions = new ArrayList<>();
        interactions.add("{\"role\":\"user\",\"tool_calls\":[{\"name\":\"test\"}]}");

        streamingWrapper.fixInteractionRole(interactions);

        assertTrue(interactions.get(0).contains("\"role\":\"user\""));
        assertFalse(interactions.get(0).contains("\"role\":\"assistant\""));
    }

    @Test
    public void testCreatePredictionRequestStreaming() {
        LLMSpec llm = mock(LLMSpec.class);
        when(llm.getModelId()).thenReturn("test-model");
        Map<String, String> parameters = new HashMap<>();
        parameters.put("key", "value");

        ActionRequest request = streamingWrapper.createPredictionRequest(llm, parameters, "tenant1");

        assertNotNull(request);
        assertTrue(request instanceof MLPredictionTaskRequest);
        MLPredictionTaskRequest mlRequest = (MLPredictionTaskRequest) request;
        assertEquals("test-model", mlRequest.getModelId());
        assertFalse(mlRequest.isDispatchTask()); // Should be false for streaming
    }

    @Test
    public void testCreatePredictionRequestNonStreaming() {
        LLMSpec llm = mock(LLMSpec.class);
        when(llm.getModelId()).thenReturn("test-model");
        Map<String, String> parameters = new HashMap<>();

        ActionRequest request = nonStreamingWrapper.createPredictionRequest(llm, parameters, "tenant1");

        MLPredictionTaskRequest mlRequest = (MLPredictionTaskRequest) request;
        assertTrue(mlRequest.isDispatchTask()); // Should be true for non-streaming
    }

    @Test
    public void testExecuteRequestStreaming() {
        MLPredictionTaskRequest request = mock(MLPredictionTaskRequest.class);

        streamingWrapper.executeRequest(request, mlTaskListener);

        verify(request).setStreamingChannel(channel);
        verify(client).execute(eq(MLPredictionStreamTaskAction.INSTANCE), eq(request), eq(mlTaskListener));
    }

    @Test
    public void testExecuteRequestNonStreaming() {
        MLPredictionTaskRequest request = mock(MLPredictionTaskRequest.class);

        nonStreamingWrapper.executeRequest(request, mlTaskListener);

        verify(request, never()).setStreamingChannel(any());
        verify(client).execute(eq(MLPredictionTaskAction.INSTANCE), eq(request), eq(mlTaskListener));
    }

    @Test
    public void testSendCompletionChunkStreaming() throws Exception {
        streamingWrapper.sendCompletionChunk("session1", "parent1");

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(channel).sendResponseBatch(responseCaptor.capture());

        MLTaskResponse response = responseCaptor.getValue();
        assertNotNull(response);
    }

    @Test
    public void testSendCompletionChunkNonStreaming() throws Exception {
        nonStreamingWrapper.sendCompletionChunk("session1", "parent1");

        verify(channel, never()).sendResponseBatch(any());
    }

    @Test
    public void testSendCompletionChunkWithException() throws Exception {
        doThrow(new RuntimeException("Channel error")).when(channel).sendResponseBatch(any());

        // Should not throw exception, just log warning
        streamingWrapper.sendCompletionChunk("session1", "parent1");

        verify(channel).sendResponseBatch(any());
    }

    @Test
    public void testSendFinalResponseStreaming() {
        streamingWrapper.sendFinalResponse("session1", listener, "parent1", true, null, null, "answer");

        verify(listener).onResponse("Streaming completed");
    }

    @Test
    public void testSendToolResponseStreaming() throws Exception {
        streamingWrapper.sendToolResponse("tool output", "session1", "parent1");

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(channel).sendResponseBatch(responseCaptor.capture());

        MLTaskResponse response = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        List<ModelTensor> tensors = output.getMlModelOutputs().get(0).getMlModelTensors();

        // Verify response tensor contains the tool output
        boolean foundContent = false;
        for (ModelTensor tensor : tensors) {
            if ("response".equals(tensor.getName()) && tensor.getDataAsMap() != null) {
                Map<String, ?> dataMap = tensor.getDataAsMap();
                if ("tool output".equals(dataMap.get("content"))) {
                    foundContent = true;
                    assertFalse((Boolean) dataMap.get("is_last"));
                }
            }
        }
        assertTrue(foundContent);
    }

    @Test
    public void testSendToolResponseNonStreaming() throws Exception {
        nonStreamingWrapper.sendToolResponse("tool output", "session1", "parent1");

        verify(channel, never()).sendResponseBatch(any());
    }

    @Test
    public void testSendToolResponseWithException() throws Exception {
        doThrow(new RuntimeException("Channel error")).when(channel).sendResponseBatch(any());

        // Should not throw exception, just log error
        streamingWrapper.sendToolResponse("tool output", "session1", "parent1");

        verify(channel).sendResponseBatch(any());
    }

    @Test
    public void testCreateStreamChunkStructure() throws Exception {
        streamingWrapper.sendCompletionChunk("test-session", "test-parent");

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(channel).sendResponseBatch(responseCaptor.capture());

        MLTaskResponse response = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) response.getOutput();
        List<ModelTensor> tensors = output.getMlModelOutputs().get(0).getMlModelTensors();

        assertEquals(3, tensors.size());

        // Find specific tensors by name
        ModelTensor memoryTensor = tensors.stream().filter(t -> "memory_id".equals(t.getName())).findFirst().orElse(null);
        ModelTensor parentTensor = tensors.stream().filter(t -> "parent_interaction_id".equals(t.getName())).findFirst().orElse(null);
        ModelTensor responseTensor = tensors.stream().filter(t -> "response".equals(t.getName())).findFirst().orElse(null);

        assertNotNull(memoryTensor);
        assertNotNull(parentTensor);
        assertNotNull(responseTensor);

        assertEquals("test-session", memoryTensor.getResult());
        assertEquals("test-parent", parentTensor.getResult());
        assertTrue((Boolean) responseTensor.getDataAsMap().get("is_last"));

    }
}
