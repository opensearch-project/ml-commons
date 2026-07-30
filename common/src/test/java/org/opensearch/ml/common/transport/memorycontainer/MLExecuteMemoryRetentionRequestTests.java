/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
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
    public void testBuilderWithTenant() {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().tenantId("tenant-1").build();
        assertNotNull(request);
        assertEquals("tenant-1", request.getTenantId());
    }

    @Test
    public void testBuilderWithoutTenant() {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().build();
        assertNotNull(request);
        assertNull(request.getTenantId());
    }

    @Test
    public void testValidateAlwaysNull() {
        // The request carries no required fields; validation is always clean.
        assertNull(MLExecuteMemoryRetentionRequest.builder().build().validate());
        assertNull(MLExecuteMemoryRetentionRequest.builder().tenantId("t").build().validate());
    }

    @Test
    public void testStreamRoundTripWithTenant() throws IOException {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().tenantId("tenant-x").build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLExecuteMemoryRetentionRequest parsed = new MLExecuteMemoryRetentionRequest(in);
        assertEquals("tenant-x", parsed.getTenantId());
    }

    @Test
    public void testStreamRoundTripWithoutTenant() throws IOException {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);

        StreamInput in = out.bytes().streamInput();
        MLExecuteMemoryRetentionRequest parsed = new MLExecuteMemoryRetentionRequest(in);
        assertNull(parsed.getTenantId());
    }

    @Test
    public void testFromActionRequestSameType() {
        MLExecuteMemoryRetentionRequest request = MLExecuteMemoryRetentionRequest.builder().tenantId("t").build();
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
                out.writeOptionalString("converted-tenant");
            }
        };

        MLExecuteMemoryRetentionRequest result = MLExecuteMemoryRetentionRequest.fromActionRequest(other);
        assertNotNull(result);
        assertEquals("converted-tenant", result.getTenantId());
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
    public void testToStringContainsTenant() {
        String s = MLExecuteMemoryRetentionRequest.builder().tenantId("tenant-str").build().toString();
        assertNotNull(s);
        assertTrue(s.contains("tenant-str"));
    }

    @Test
    public void testInstanceOfActionRequest() {
        assertTrue(MLExecuteMemoryRetentionRequest.builder().build() instanceof ActionRequest);
    }
}
