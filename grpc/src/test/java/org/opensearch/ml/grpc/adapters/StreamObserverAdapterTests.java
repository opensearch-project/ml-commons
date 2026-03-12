/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.java.org.opensearch.ml.grpc.adapters;

import static org.junit.Assert.assertFalse;
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
        adapter = new StreamObserverAdapter<>(mockObserver, false);
    }

    @Test
    public void testOnResponseWithLastChunk() {
        // Create a response with is_last = true
        Map<String, Object> dataAsMap = Map.of("content", "test", "is_last", true);
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertTrue("Observer should have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testOnResponseWithNonLastChunk() {
        // Create a response with is_last = false
        Map<String, Object> dataAsMap = Map.of("content", "test", "is_last", false);
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
    }

    @Test
    public void testOnResponseWithoutIsLastFlag() {
        // Create a response without is_last flag (should default to false)
        Map<String, Object> dataAsMap = Map.of("content", "test");
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        adapter.onResponse(response);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have received onCompleted", mockObserver.completedCalled);
        assertFalse("Observer should not have received onError", mockObserver.errorCalled);
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
    public void testMultipleChunks() {
        // First chunk (not last)
        Map<String, Object> data1 = Map.of("content", "chunk1", "is_last", false);
        ModelTensor tensor1 = ModelTensor.builder().name("response").dataAsMap(data1).build();
        ModelTensors tensors1 = ModelTensors.builder().mlModelTensors(List.of(tensor1)).build();
        ModelTensorOutput output1 = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors1)).build();
        MLTaskResponse response1 = MLTaskResponse.builder().output(output1).build();

        adapter.onResponse(response1);

        assertTrue("Observer should have received first onNext", mockObserver.nextCalled);
        assertFalse("Observer should not have completed after first chunk", mockObserver.completedCalled);

        // Reset for second chunk
        mockObserver.reset();

        // Second chunk (last)
        Map<String, Object> data2 = Map.of("content", "chunk2", "is_last", true);
        ModelTensor tensor2 = ModelTensor.builder().name("response").dataAsMap(data2).build();
        ModelTensors tensors2 = ModelTensors.builder().mlModelTensors(List.of(tensor2)).build();
        ModelTensorOutput output2 = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors2)).build();
        MLTaskResponse response2 = MLTaskResponse.builder().output(output2).build();

        adapter.onResponse(response2);

        assertTrue("Observer should have received second onNext", mockObserver.nextCalled);
        assertTrue("Observer should have completed after last chunk", mockObserver.completedCalled);
    }

    @Test
    public void testOnStreamResponseExplicitLast() {
        Map<String, Object> dataAsMap = Map.of("content", "test");
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        // Explicitly mark as last
        adapter.onStreamResponse(response, true);

        assertTrue("Observer should have received onNext", mockObserver.nextCalled);
        assertTrue("Observer should have received onCompleted", mockObserver.completedCalled);
    }

    @Test
    public void testIsLastDetectionWithStringValue() {
        // Test with is_last as String "true"
        Map<String, Object> dataAsMap = Map.of("content", "test", "is_last", "true");
        ModelTensor tensor = ModelTensor.builder().name("response").dataAsMap(dataAsMap).build();
        ModelTensors tensors = ModelTensors.builder().mlModelTensors(List.of(tensor)).build();
        ModelTensorOutput output = ModelTensorOutput.builder().mlModelOutputs(List.of(tensors)).build();
        MLTaskResponse response = MLTaskResponse.builder().output(output).build();

        adapter.onResponse(response);

        assertTrue("Observer should have received onCompleted with string 'true'", mockObserver.completedCalled);
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
