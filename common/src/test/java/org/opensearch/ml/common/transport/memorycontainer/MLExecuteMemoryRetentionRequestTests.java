/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLExecuteMemoryRetentionRequestTests {

    @Test
    public void testBuilder() {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().build();
        assertNotNull(request);
    }

    @Test
    public void testValidateAlwaysNull() {
        // The request carries no fields; validation is always clean.
        assertNull(MLExecuteMemoryRetentionRequest.builder().build().validate());
    }

    @Test
    public void testStreamRoundTrip() throws IOException {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLExecuteMemoryRetentionRequest parsed = new MLExecuteMemoryRetentionRequest(in);
        assertNotNull(parsed);
    }

    @Test
    public void testFromActionRequestSameType() {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().build();
        assertSame(request, MLExecuteMemoryRetentionRequest.fromActionRequest(request));
    }

    @Test
    public void testFromActionRequestDifferentType() throws IOException {
        ActionRequest other = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                super.writeTo(out);
            }
        };

        MLExecuteMemoryRetentionRequest result = MLExecuteMemoryRetentionRequest.fromActionRequest(other);
        assertNotNull(result);
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
        ActionRequest broken = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("boom");
            }
        };
        MLExecuteMemoryRetentionRequest.fromActionRequest(broken);
    }

    @Test
    public void testInstanceOfActionRequest() {
        assertTrue(MLExecuteMemoryRetentionRequest.builder().build() instanceof ActionRequest);
    }
}
