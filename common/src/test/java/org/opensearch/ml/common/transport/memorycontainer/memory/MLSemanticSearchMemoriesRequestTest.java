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

public class MLSemanticSearchMemoriesRequestTest {

    @Test
    public void testSerialization() throws IOException {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest
            .builder()
            .mlSemanticSearchMemoriesInput(input)
            .tenantId("t1")
            .build();

        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLSemanticSearchMemoriesRequest deserialized = new MLSemanticSearchMemoriesRequest(in);

        assertEquals("c1", deserialized.getMlSemanticSearchMemoriesInput().getMemoryContainerId());
        assertEquals("test", deserialized.getMlSemanticSearchMemoriesInput().getQuery());
        assertEquals("t1", deserialized.getTenantId());
    }

    @Test
    public void testValidateSuccess() {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(input).build();
        assertNull(request.validate());
    }

    @Test
    public void testValidateNullInput() {
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(null).build();
        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertTrue(e.getMessage().contains("can't be null"));
    }

    @Test
    public void testFromActionRequest() {
        MLSemanticSearchMemoriesInput input = MLSemanticSearchMemoriesInput.builder().memoryContainerId("c1").query("test").build();
        MLSemanticSearchMemoriesRequest request = MLSemanticSearchMemoriesRequest.builder().mlSemanticSearchMemoriesInput(input).build();
        assertSame(request, MLSemanticSearchMemoriesRequest.fromActionRequest(request));
    }
}
