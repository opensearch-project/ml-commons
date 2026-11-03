/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLHybridSearchMemoriesRequestTest {

    @Test
    public void testSerialization() throws IOException {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest
            .builder()
            .mlHybridSearchMemoriesInput(input)
            .tenantId("t1")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLHybridSearchMemoriesRequest deserialized = new MLHybridSearchMemoriesRequest(in);

        assertEquals("c1", deserialized.getMlHybridSearchMemoriesInput().getMemoryContainerId());
        assertEquals("t1", deserialized.getTenantId());
    }

    @Test
    public void testValidateSuccess() {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(input).build();
        assertNull(request.validate());
    }

    @Test
    public void testValidateNullInput() {
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(null).build();
        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertTrue(e.getMessage().contains("can't be null"));
    }

    @Test
    public void testFromActionRequest() {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLHybridSearchMemoriesRequest request = MLHybridSearchMemoriesRequest.builder().mlHybridSearchMemoriesInput(input).build();
        assertSame(request, MLHybridSearchMemoriesRequest.fromActionRequest(request));
    }

    @Test
    public void testFromActionRequest_SerializationPath() throws IOException {
        MLHybridSearchMemoriesInput input = MLHybridSearchMemoriesInput.builder().memoryContainerId("c1").query("test query").k(5).build();
        MLHybridSearchMemoriesRequest original = MLHybridSearchMemoriesRequest
            .builder()
            .mlHybridSearchMemoriesInput(input)
            .tenantId("t1")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLHybridSearchMemoriesRequest deserialized = new MLHybridSearchMemoriesRequest(in);

        MLHybridSearchMemoriesRequest fromAction = MLHybridSearchMemoriesRequest.fromActionRequest(deserialized);
        assertEquals("c1", fromAction.getMlHybridSearchMemoriesInput().getMemoryContainerId());
        assertEquals("test query", fromAction.getMlHybridSearchMemoriesInput().getQuery());
        assertEquals("t1", fromAction.getTenantId());
    }
}
