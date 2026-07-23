/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLMcpConnectorListToolsRequestTest {

    private final static String connectorId = "ZflqvZwBq1rC3seS3wJd";
    private final static String tenantId = "tenant-1";

    @Test
    public void testBuilder() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId(connectorId)
            .tenantId(tenantId)
            .build();
        assertEquals(connectorId, request.getConnectorId());
        assertEquals(tenantId, request.getTenantId());
    }

    @Test
    public void testBuilder_NullTenantId() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId(connectorId).build();
        assertEquals(connectorId, request.getConnectorId());
        assertNull(request.getTenantId());
    }

    @Test
    public void testValidate_Success() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId(connectorId)
            .tenantId(tenantId)
            .build();
        ActionRequestValidationException e = request.validate();
        assertNull(e);
    }

    @Test
    public void testValidate_NullConnectorId() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId(null).build();
        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertTrue(e.getMessage().contains("connector_id cannot be null or empty"));
    }

    @Test
    public void testValidate_EmptyConnectorId() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId("").build();
        ActionRequestValidationException e = request.validate();
        assertNotNull(e);
        assertTrue(e.getMessage().contains("connector_id cannot be null or empty"));
    }

    @Test
    public void testWriteToReadFrom_RoundTrip() throws IOException {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId(connectorId)
            .tenantId(tenantId)
            .build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLMcpConnectorListToolsRequest parsed = new MLMcpConnectorListToolsRequest(out.bytes().streamInput());
        assertEquals(connectorId, parsed.getConnectorId());
        assertEquals(tenantId, parsed.getTenantId());
    }

    @Test
    public void testWriteToReadFrom_RoundTrip_NullTenantId() throws IOException {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest.builder().connectorId(connectorId).tenantId(null).build();
        BytesStreamOutput out = new BytesStreamOutput();
        request.writeTo(out);
        MLMcpConnectorListToolsRequest parsed = new MLMcpConnectorListToolsRequest(out.bytes().streamInput());
        assertEquals(connectorId, parsed.getConnectorId());
        assertNull(parsed.getTenantId());
    }

    @Test
    public void testFromActionRequest_SameType() {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId(connectorId)
            .tenantId(tenantId)
            .build();
        MLMcpConnectorListToolsRequest result = MLMcpConnectorListToolsRequest.fromActionRequest(request);
        assertSame(request, result);
    }

    @Test
    public void testFromActionRequest_OtherType() throws IOException {
        MLMcpConnectorListToolsRequest request = MLMcpConnectorListToolsRequest
            .builder()
            .connectorId(connectorId)
            .tenantId(tenantId)
            .build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                request.writeTo(out);
            }
        };
        MLMcpConnectorListToolsRequest result = MLMcpConnectorListToolsRequest.fromActionRequest(actionRequest);
        assertNotSame(request, result);
        assertEquals(connectorId, result.getConnectorId());
        assertEquals(tenantId, result.getTenantId());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLMcpConnectorListToolsRequest.fromActionRequest(actionRequest);
    }
}
