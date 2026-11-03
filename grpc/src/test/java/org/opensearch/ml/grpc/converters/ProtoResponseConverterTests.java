/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.converters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.common.transport.execute.MLExecuteTaskResponse;
import org.opensearch.protobufs.PredictResponse;

/**
 * Unit tests for ProtoResponseConverter.
 */
public class ProtoResponseConverterTests {

    @Test
    public void testToProto_mlTaskResponse_singleTensor() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("content", "hello", "is_last", false)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertNotNull(result);
        assertEquals(1, result.getInferenceResultsCount());
        assertEquals(1, result.getInferenceResults(0).getOutputCount());
        assertEquals("response", result.getInferenceResults(0).getOutput(0).getName());
        assertEquals("hello", result.getInferenceResults(0).getOutput(0).getDataAsMap().getContent());
        assertFalse(result.getInferenceResults(0).getOutput(0).getDataAsMap().getIsLast());
    }

    @Test
    public void testToProto_mlTaskResponse_isLastTrue() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("content", "done", "is_last", true)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertTrue(result.getInferenceResults(0).getOutput(0).getDataAsMap().getIsLast());
    }

    @Test
    public void testToProto_mlTaskResponse_withResult() {
        ModelTensor tensor = ModelTensor.builder().name("answer").result("some result").build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertEquals("answer", result.getInferenceResults(0).getOutput(0).getName());
        assertEquals("some result", result.getInferenceResults(0).getOutput(0).getResult());
    }

    @Test
    public void testToProto_mlTaskResponse_multipleTensors() {
        ModelTensor tensor1 = ModelTensor.builder().name("t1").dataAsMap(Map.of("content", "chunk1", "is_last", false)).build();
        ModelTensor tensor2 = ModelTensor.builder().name("t2").dataAsMap(Map.of("content", "chunk2", "is_last", true)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor1, tensor2)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertEquals(1, result.getInferenceResultsCount());
        assertEquals(2, result.getInferenceResults(0).getOutputCount());
        assertEquals("t1", result.getInferenceResults(0).getOutput(0).getName());
        assertEquals("t2", result.getInferenceResults(0).getOutput(1).getName());
    }

    @Test
    public void testToProto_mlTaskResponse_multipleInferenceResults() {
        ModelTensor tensor1 = ModelTensor.builder().name("r1").dataAsMap(Map.of("content", "a", "is_last", false)).build();
        ModelTensor tensor2 = ModelTensor.builder().name("r2").dataAsMap(Map.of("content", "b", "is_last", true)).build();
        ModelTensors tensors1 = ModelTensors.builder().mlModelTensors(List.of(tensor1)).build();
        ModelTensors tensors2 = ModelTensors.builder().mlModelTensors(List.of(tensor2)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors1, tensors2)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertEquals(2, result.getInferenceResultsCount());
    }

    @Test
    public void testToProto_executeTaskResponse() {
        ModelTensor tensor = ModelTensor
            .builder()
            .name("agent_output")
            .dataAsMap(Map.of("content", "agent response", "is_last", true))
            .build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLExecuteTaskResponse response = MLExecuteTaskResponse.builder().functionName(FunctionName.AGENT).output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertNotNull(result);
        assertEquals("agent_output", result.getInferenceResults(0).getOutput(0).getName());
        assertEquals("agent response", result.getInferenceResults(0).getOutput(0).getDataAsMap().getContent());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToProto_unsupportedResponseType() {
        ProtoResponseConverter.toProto("not a valid response");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testToProto_nullOutput() {
        MLTaskResponse response = MLTaskResponse.builder().output(null).build();
        ProtoResponseConverter.toProto(response);
    }

    @Test
    public void testToProto_nullMlModelOutputs() {
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(null).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertNotNull(result);
        assertEquals(0, result.getInferenceResultsCount());
    }

    @Test
    public void testToProto_emptyMlModelOutputs() {
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(Collections.emptyList()).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertNotNull(result);
        assertEquals(0, result.getInferenceResultsCount());
    }

    @Test
    public void testToProto_nullTensorsList() {
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(null).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertEquals(1, result.getInferenceResultsCount());
        assertEquals(0, result.getInferenceResults(0).getOutputCount());
    }

    @Test
    public void testToProto_tensorWithNullName() {
        ModelTensor tensor = ModelTensor.builder().name(null).dataAsMap(Map.of("content", "test", "is_last", false)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertNotNull(result);
        assertEquals("", result.getInferenceResults(0).getOutput(0).getName());
    }

    @Test
    public void testToProto_missingContentInDataMap() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("is_last", false)).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        // Missing content defaults to empty string
        assertEquals("", result.getInferenceResults(0).getOutput(0).getDataAsMap().getContent());
    }

    @Test
    public void testToProto_missingIsLastInDataMap() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Map.of("content", "test")).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        // Missing is_last defaults to false
        assertFalse(result.getInferenceResults(0).getOutput(0).getDataAsMap().getIsLast());
    }

    @Test
    public void testToProto_emptyDataMap() {
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(Collections.emptyMap()).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        // Empty dataAsMap should not set dataAsMap on the output
        assertNotNull(result);
    }

    @Test
    public void testToProto_tensorWithNoDataMap() {
        ModelTensor tensor = ModelTensor.builder().name("response").result("plain result").build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        PredictResponse result = ProtoResponseConverter.toProto(response);

        assertEquals("response", result.getInferenceResults(0).getOutput(0).getName());
        assertEquals("plain result", result.getInferenceResults(0).getOutput(0).getResult());
    }
}
