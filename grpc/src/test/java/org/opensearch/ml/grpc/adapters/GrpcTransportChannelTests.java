/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;

/**
 * Unit tests for GrpcTransportChannel.
 */
public class GrpcTransportChannelTests {

    private MockStreamObserver mockObserver;
    private StreamObserverAdapter<Object> adapter;
    private GrpcTransportChannel channel;

    @Before
    public void setUp() {
        mockObserver = new MockStreamObserver();
        adapter = new StreamObserverAdapter<>(mockObserver);
        channel = (GrpcTransportChannel) adapter.getChannel();
    }

    @Test
    public void testGetProfileName() {
        assertEquals("grpc", channel.getProfileName());
    }

    @Test
    public void testGetChannelType() {
        assertEquals("grpc", channel.getChannelType());
    }

    @Test
    public void testIsCompleted_initiallyFalse() {
        assertFalse(channel.isCompleted());
    }

    @Test
    public void testMarkCompleted() {
        channel.markCompleted();
        assertTrue(channel.isCompleted());
    }

    @Test
    public void testSendResponseException() {
        Exception exception = new RuntimeException("test error");

        channel.sendResponse(exception);

        assertTrue("Should send error to observer", mockObserver.errorCalled);
        assertTrue("Channel should be completed", channel.isCompleted());
    }

    @Test
    public void testSendResponseException_alreadyCompleted() {
        channel.markCompleted();

        channel.sendResponse(new RuntimeException("test error"));

        assertFalse("Should not send error when already completed", mockObserver.errorCalled);
    }

    @Test
    public void testSendResponseTransportResponse() {
        TransportResponse mockResponse = mock(TransportResponse.class);

        channel.sendResponse(mockResponse);

        assertFalse("Should not call onNext", mockObserver.nextCalled);
        assertFalse("Should not call onError", mockObserver.errorCalled);
        assertFalse("Should not call onCompleted", mockObserver.completedCalled);
    }

    @Test
    public void testSendResponseBatch_withMLTaskResponse() {
        MLTaskResponse response = createMLTaskResponse("test content");

        channel.sendResponseBatch(response);

        assertTrue("Should send response via adapter", mockObserver.nextCalled);
    }

    @Test
    public void testSendResponseBatch_alreadyCompleted() {
        channel.markCompleted();

        MLTaskResponse response = createMLTaskResponse("test content");
        channel.sendResponseBatch(response);

        assertFalse("Should not send when completed", mockObserver.nextCalled);
    }

    @Test
    public void testSendResponseBatch_withNullAdapter() {
        GrpcTransportChannel channelNoAdapter = new GrpcTransportChannel(mockObserver, null);

        MLTaskResponse response = createMLTaskResponse("test content");
        channelNoAdapter.sendResponseBatch(response);

        assertFalse("Should not send without adapter", mockObserver.nextCalled);
    }

    @Test
    public void testSendResponseBatch_unsupportedResponseType() {
        TransportResponse unsupported = mock(TransportResponse.class);

        channel.sendResponseBatch(unsupported);

        assertFalse("Should not call onNext for unsupported type", mockObserver.nextCalled);
    }

    @Test
    public void testCompleteStream() {
        channel.completeStream();

        assertTrue("Should complete the stream", mockObserver.completedCalled);
        assertTrue("Channel should be completed", channel.isCompleted());
    }

    @Test
    public void testCompleteStream_alreadyCompleted() {
        channel.completeStream();
        mockObserver.reset();

        channel.completeStream();

        assertFalse("Should not complete twice", mockObserver.completedCalled);
    }

    @Test
    public void testCompleteStream_withNullAdapter() {
        GrpcTransportChannel channelNoAdapter = new GrpcTransportChannel(mockObserver, null);

        channelNoAdapter.completeStream();

        assertTrue("Channel should be marked completed", channelNoAdapter.isCompleted());
        assertFalse("Should not call onCompleted without adapter", mockObserver.completedCalled);
    }

    @Test
    public void testSendResponseException_thenSendResponseBatch_ignored() {
        channel.sendResponse(new RuntimeException("error"));
        mockObserver.reset();

        MLTaskResponse response = createMLTaskResponse("test");
        channel.sendResponseBatch(response);

        assertFalse("Should not send batch after error", mockObserver.nextCalled);
    }

    @Test
    public void testCompleteStream_thenSendResponseBatch_ignored() {
        channel.completeStream();
        mockObserver.reset();

        MLTaskResponse response = createMLTaskResponse("test");
        channel.sendResponseBatch(response);

        assertFalse("Should not send batch after completion", mockObserver.nextCalled);
    }

    private MLTaskResponse createMLTaskResponse(String content) {
        Map<String, Object> dataAsMap = Map.of("content", content, "is_last", false);
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        return MLTaskResponse.builder().output(output).build();
    }

    /**
     * Mock implementation of StreamObserver for testing.
     */
    private static class MockStreamObserver implements io.grpc.stub.StreamObserver<Object> {
        boolean nextCalled = false;
        boolean errorCalled = false;
        boolean completedCalled = false;
        Object lastValue = null;
        Throwable lastError = null;

        @Override
        public void onNext(Object value) {
            nextCalled = true;
            lastValue = value;
        }

        @Override
        public void onError(Throwable t) {
            errorCalled = true;
            lastError = t;
        }

        @Override
        public void onCompleted() {
            completedCalled = true;
        }

        void reset() {
            nextCalled = false;
            errorCalled = false;
            completedCalled = false;
            lastValue = null;
            lastError = null;
        }
    }
}
