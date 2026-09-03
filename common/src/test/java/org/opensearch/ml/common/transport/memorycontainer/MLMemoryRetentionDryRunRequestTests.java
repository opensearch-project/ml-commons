/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

public class MLMemoryRetentionDryRunRequestTests {

    @Test
    public void testSingleContainerRequest() {
        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest
            .builder()
            .memoryContainerId("container-1")
            .tenantId("tenant-1")
            .build();
        assertEquals("container-1", request.getMemoryContainerId());
        assertEquals("tenant-1", request.getTenantId());
        assertFalse(request.isClusterWide());
        assertNull(request.validate());
    }

    @Test
    public void testClusterWideRequest() {
        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId(null).build();
        assertTrue(request.isClusterWide());
        // cluster-wide is valid (no container id required)
        assertNull(request.validate());
    }

    @Test
    public void testStreamRoundTripSingle() throws IOException {
        MLMemoryRetentionDryRunRequest original = MLMemoryRetentionDryRunRequest
            .builder()
            .memoryContainerId("container-xyz")
            .tenantId("tenant-abc")
            .build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemoryRetentionDryRunRequest parsed = new MLMemoryRetentionDryRunRequest(in);
        assertEquals("container-xyz", parsed.getMemoryContainerId());
        assertEquals("tenant-abc", parsed.getTenantId());
        assertFalse(parsed.isClusterWide());
    }

    @Test
    public void testStreamRoundTripClusterWide() throws IOException {
        MLMemoryRetentionDryRunRequest original = MLMemoryRetentionDryRunRequest.builder().build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLMemoryRetentionDryRunRequest parsed = new MLMemoryRetentionDryRunRequest(in);
        assertNull(parsed.getMemoryContainerId());
        assertTrue(parsed.isClusterWide());
    }

    @Test
    public void testFromActionRequestSameInstance() {
        MLMemoryRetentionDryRunRequest request = MLMemoryRetentionDryRunRequest.builder().memoryContainerId("c").build();
        assertSame(request, MLMemoryRetentionDryRunRequest.fromActionRequest(request));
    }

    @Test
    public void testFromActionRequestConverts() {
        MLMemoryRetentionDryRunRequest original = MLMemoryRetentionDryRunRequest
            .builder()
            .memoryContainerId("container-9")
            .tenantId("t9")
            .build();
        ActionRequest wrapper = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                original.writeTo(out);
            }
        };
        MLMemoryRetentionDryRunRequest converted = MLMemoryRetentionDryRunRequest.fromActionRequest(wrapper);
        assertNotNull(converted);
        assertEquals("container-9", converted.getMemoryContainerId());
        assertEquals("t9", converted.getTenantId());
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
        MLMemoryRetentionDryRunRequest.fromActionRequest(broken);
    }
}
