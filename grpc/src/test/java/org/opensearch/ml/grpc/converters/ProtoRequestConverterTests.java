/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.mockito.Answers;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.dataset.remote.RemoteInferenceInputDataSet;
import org.opensearch.ml.common.input.execute.agent.AgentMLInput;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskRequest;
import org.opensearch.ml.common.transport.prediction.MLPredictionTaskRequest;
import org.opensearch.protobufs.MlExecuteAgentStreamRequest;
import org.opensearch.protobufs.MlPredictModelStreamRequest;
import org.opensearch.protobufs.Parameters;

import com.google.protobuf.Descriptors;

/**
 * Unit tests for ProtoRequestConverter.
 */
public class ProtoRequestConverterTests {

    @Test(expected = IllegalArgumentException.class)
    public void testToPredictRequest_emptyModelId() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class);
        when(request.getModelId()).thenReturn("");

        ProtoRequestConverter.toPredictRequest(request, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToPredictRequest_noRequestBody() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class);
        when(request.getModelId()).thenReturn("test-model-id");
        when(request.hasMlPredictModelStreamRequestBody()).thenReturn(false);

        ProtoRequestConverter.toPredictRequest(request, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToPredictRequest_noParameters() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        when(request.getModelId()).thenReturn("test-model-id");
        when(request.hasMlPredictModelStreamRequestBody()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().hasParameters()).thenReturn(false);

        ProtoRequestConverter.toPredictRequest(request, null);
    }

    @Test
    public void testToPredictRequest_success() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        when(request.getModelId()).thenReturn("test-model-id");
        when(request.hasMlPredictModelStreamRequestBody()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(Map.of());

        MLPredictionTaskRequest result = ProtoRequestConverter.toPredictRequest(request, null);

        assertNotNull(result);
        assertEquals("test-model-id", result.getModelId());
        assertEquals(FunctionName.REMOTE, result.getMlInput().getAlgorithm());
        assertTrue(result.getMlInput().getInputDataset() instanceof RemoteInferenceInputDataSet);

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) result.getMlInput().getInputDataset();
        assertEquals("true", inputDataSet.getParameters().get("stream"));
    }

    @Test
    public void testToPredictRequest_withTenantId() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        when(request.getModelId()).thenReturn("test-model-id");
        when(request.hasMlPredictModelStreamRequestBody()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(Map.of());

        MLPredictionTaskRequest result = ProtoRequestConverter.toPredictRequest(request, "tenant-123");

        assertEquals("tenant-123", result.getTenantId());
    }

    @Test
    public void testToPredictRequest_withStringParameter() {
        MlPredictModelStreamRequest request = mock(MlPredictModelStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        Descriptors.FieldDescriptor fieldDescriptor = mock(Descriptors.FieldDescriptor.class);
        when(fieldDescriptor.getJsonName()).thenReturn("inputs");
        when(fieldDescriptor.isRepeated()).thenReturn(false);
        when(fieldDescriptor.getType()).thenReturn(Descriptors.FieldDescriptor.Type.STRING);

        Map<Descriptors.FieldDescriptor, Object> fields = new HashMap<>();
        fields.put(fieldDescriptor, "test input");

        when(request.getModelId()).thenReturn("test-model-id");
        when(request.hasMlPredictModelStreamRequestBody()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlPredictModelStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(fields);

        MLPredictionTaskRequest result = ProtoRequestConverter.toPredictRequest(request, null);

        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) result.getMlInput().getInputDataset();
        assertEquals("test input", inputDataSet.getParameters().get("inputs"));
        assertEquals("true", inputDataSet.getParameters().get("stream"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToExecuteRequest_emptyAgentId() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class);
        when(request.getAgentId()).thenReturn("");

        ProtoRequestConverter.toExecuteRequest(request, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToExecuteRequest_noRequestBody() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class);
        when(request.getAgentId()).thenReturn("test-agent-id");
        when(request.hasMlExecuteAgentStreamRequestBody()).thenReturn(false);

        ProtoRequestConverter.toExecuteRequest(request, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToExecuteRequest_noParameters() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        when(request.getAgentId()).thenReturn("test-agent-id");
        when(request.hasMlExecuteAgentStreamRequestBody()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().hasParameters()).thenReturn(false);

        ProtoRequestConverter.toExecuteRequest(request, null);
    }

    @Test
    public void testToExecuteRequest_success() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        when(request.getAgentId()).thenReturn("test-agent-id");
        when(request.hasMlExecuteAgentStreamRequestBody()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(Map.of());

        MLExecuteTaskRequest result = ProtoRequestConverter.toExecuteRequest(request, null);

        assertNotNull(result);
        assertEquals(FunctionName.AGENT, result.getFunctionName());
        assertTrue(result.getInput() instanceof AgentMLInput);

        AgentMLInput agentInput = (AgentMLInput) result.getInput();
        assertEquals("test-agent-id", agentInput.getAgentId());
    }

    @Test
    public void testToExecuteRequest_withTenantId() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        when(request.getAgentId()).thenReturn("test-agent-id");
        when(request.hasMlExecuteAgentStreamRequestBody()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(Map.of());

        MLExecuteTaskRequest result = ProtoRequestConverter.toExecuteRequest(request, "tenant-456");

        AgentMLInput agentInput = (AgentMLInput) result.getInput();
        assertEquals("tenant-456", agentInput.getTenantId());
    }

    @Test
    public void testToExecuteRequest_streamParameterAdded() {
        MlExecuteAgentStreamRequest request = mock(MlExecuteAgentStreamRequest.class, Answers.RETURNS_DEEP_STUBS);
        Parameters parameters = mock(Parameters.class);

        when(request.getAgentId()).thenReturn("test-agent-id");
        when(request.hasMlExecuteAgentStreamRequestBody()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().hasParameters()).thenReturn(true);
        when(request.getMlExecuteAgentStreamRequestBody().getParameters()).thenReturn(parameters);
        when(parameters.getAllFields()).thenReturn(Map.of());

        MLExecuteTaskRequest result = ProtoRequestConverter.toExecuteRequest(request, null);

        AgentMLInput agentInput = (AgentMLInput) result.getInput();
        RemoteInferenceInputDataSet inputDataSet = (RemoteInferenceInputDataSet) agentInput.getInputDataset();
        assertEquals("true", inputDataSet.getParameters().get("stream"));
    }
}
