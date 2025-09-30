/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.transport.MLTaskResponse;
import org.opensearch.ml.engine.algorithms.remote.streaming.StreamPredictActionListener;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;

public class StreamPredictActionListenerTest {

    @Mock
    private TransportChannel mockChannel;

    @Mock
    private MLTaskResponse mockResponse;

    private StreamPredictActionListener<MLTaskResponse, TransportRequest> listener;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        listener = new StreamPredictActionListener<>(mockChannel);
    }

    @Test
    public void testOnStreamResponse_NotLastBatch() {
        listener.onStreamResponse(mockResponse, false);

        verify(mockChannel).sendResponseBatch(mockResponse);
        verify(mockChannel, never()).completeStream();
    }

    @Test
    public void testOnStreamResponse_LastBatch() {
        listener.onStreamResponse(mockResponse, true);

        verify(mockChannel).sendResponseBatch(mockResponse);
        verify(mockChannel).completeStream();
    }

    @Test
    public void testOnResponse_CallsOnStreamResponseWithLastBatch() {
        listener.onResponse(mockResponse);

        verify(mockChannel).sendResponseBatch(mockResponse);
    }

    @Test
    public void testOnFailure_WithErrorMessage() {
        Exception testException = new RuntimeException("Test error message");

        listener.onFailure(testException);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(mockChannel).sendResponseBatch(responseCaptor.capture());
        verify(mockChannel).completeStream();

        MLTaskResponse errorResponse = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) errorResponse.getOutput();
        ModelTensor errorTensor = output.getMlModelOutputs().get(0).getMlModelTensors().get(0);

        assertEquals("error", errorTensor.getName());
        assertEquals("Test error message", errorTensor.getDataAsMap().get("error"));
        assertEquals(true, errorTensor.getDataAsMap().get("is_last"));
    }

    @Test
    public void testOnFailure_WithNullErrorMessage() {
        Exception testException = new RuntimeException((String) null);

        listener.onFailure(testException);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(mockChannel).sendResponseBatch(responseCaptor.capture());
        verify(mockChannel).completeStream();

        MLTaskResponse errorResponse = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) errorResponse.getOutput();
        ModelTensor errorTensor = output.getMlModelOutputs().get(0).getMlModelTensors().get(0);

        assertEquals("error", errorTensor.getName());
        assertEquals("Request failed", errorTensor.getDataAsMap().get("error"));
        assertEquals(true, errorTensor.getDataAsMap().get("is_last"));
    }

    @Test
    public void testOnFailure_WithEmptyErrorMessage() {
        Exception testException = new RuntimeException("  ");

        listener.onFailure(testException);

        ArgumentCaptor<MLTaskResponse> responseCaptor = ArgumentCaptor.forClass(MLTaskResponse.class);
        verify(mockChannel).sendResponseBatch(responseCaptor.capture());

        MLTaskResponse errorResponse = responseCaptor.getValue();
        ModelTensorOutput output = (ModelTensorOutput) errorResponse.getOutput();
        ModelTensor errorTensor = output.getMlModelOutputs().get(0).getMlModelTensors().get(0);

        assertEquals("Request failed", errorTensor.getDataAsMap().get("error"));
    }

    @Test
    public void testOnFailure_SendResponseBatchThrowsException() {
        Exception testException = new RuntimeException("Test error");
        doThrow(new RuntimeException("Send batch failed")).when(mockChannel).sendResponseBatch(any());

        listener.onFailure(testException);

        verify(mockChannel).sendResponseBatch(any(MLTaskResponse.class));
        verify(mockChannel).completeStream();
    }
}
