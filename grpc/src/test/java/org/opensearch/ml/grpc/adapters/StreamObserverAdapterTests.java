/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.grpc.adapters;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.ml.common.output.model.ModelTensor;
import org.opensearch.ml.common.output.model.ModelTensorOutput;
import org.opensearch.ml.common.output.model.ModelTensors;
import org.opensearch.ml.common.transport.MLTaskResponse;

/**
 * Unit tests for StreamObserverAdapter.
 */
public class StreamObserverAdapterTests {

    private MockStreamObserver mockObserver;
    private StreamObserverAdapter<Object> adapter;

    @Before
    public void setUp() {
        mockObserver = new MockStreamObserver();
        adapter = new StreamObserverAdapter<>(mockObserver);
    }

    @Test
    public void testGetChannel() {
        assertNotNull("Channel should not be null", adapter.getChannel());
        assertTrue("Channel should be GrpcTransportChannel", adapter.getChannel() instanceof GrpcTransportChannel);
    }

    @Test
    public void testOnResponse_sendsNextButDoesNotComplete() {
        MLTaskResponse response = createResponse("test", true);

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("onResponse should not complete the stream", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testOnResponse_nonLastChunk() {
        MLTaskResponse response = createResponse("test", false);

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testOnResponse_withoutIsLastFlag() {
        Map<String, Object> dataAsMap = Map.of("content", "test");
        MLTaskResponse response = createResponseFromMap(dataAsMap);

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testOnStreamResponse_explicitLast() {
        MLTaskResponse response = createResponse("test", false);

        adapter.onStreamResponse(response, true);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertTrue("Observer should have received onCompleted", mockObserver.completedCalled);
    }

    @Test
    public void testOnStreamResponse_notLast() {
        MLTaskResponse response = createResponse("test", false);

        adapter.onStreamResponse(response, false);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
    }

    @Test
    public void testOnFailure() {
        Exception exception = new RuntimeException("Test error");

        adapter.onFailure(exception);

        assertFalse("Observer should not have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
        assertTrue("Observer should have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testMultipleChunks_thenExplicitComplete() {
        // First chunk
        MLTaskResponse response1 = createResponse("chunk1", false);
        adapter.onResponse(response1);

        assertTrue("Observer should have received first onNext", mockObserver.nextCalled);
        assertFalse("Should not have completed after first chunk", mockObserver.completedCalled);

        mockObserver.reset();

        // Second chunk
        MLTaskResponse response2 = createResponse("chunk2", false);
        adapter.onResponse(response2);

        assertTrue("Observer should have received second onNext", mockObserver.nextCalled);
        assertFalse("Should not have completed after second chunk", mockObserver.completedCalled);

        mockObserver.reset();

        // Final chunk via onStreamResponse with isLast=true
        MLTaskResponse response3 = createResponse("chunk3", false);
        adapter.onStreamResponse(response3, true);

        assertTrue("Observer should have received third onNext", mockObserver.nextCalled);
        assertTrue("Should have completed after explicit last", mockObserver.completedCalled);
    }

    @Test
    public void testHandleStreamResponse_afterCompleted_ignored() {
        MLTaskResponse response = createResponse("test", false);

        adapter.onStreamResponse(response, true);
        assertTrue(mockObserver.completedCalled);

        mockObserver.reset();

        // Further responses should be ignored
        adapter.handleStreamResponse(createResponse("ignored", false), false);
        assertFalse("Should not send after completion", mockObserver.nextCalled);
    }

    @Test
    public void testCompleteGrpcStream() {
        adapter.completeGrpcStream();

        assertTrue("Observer should have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onNext", mockObserver.nextCalled);
    }

    @Test
    public void testCompleteGrpcStream_calledTwice() {
        adapter.completeGrpcStream();
        assertTrue(mockObserver.completedCalled);

        mockObserver.reset();
        adapter.completeGrpcStream();

        assertFalse("Should not complete twice", mockObserver.completedCalled);
    }

    @Test
    public void testOnFailure_afterCompletion() {
        adapter.completeGrpcStream();
        mockObserver.reset();

        // onFailure still sends error even after completion
        // (this is expected gRPC behavior - the observer handles it)
        adapter.onFailure(new RuntimeException("error"));

        assertTrue("Should still send error", mockObserver.errorCalled);
    }

    private MLTaskResponse createResponse(String content, boolean isLast) {
        Map<String, Object> dataAsMap = Map.of("content", content, "is_last", isLast);
        return createResponseFromMap(dataAsMap);
    }

    private MLTaskResponse createResponseFromMap(Map<String, Object> dataAsMap) {
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
