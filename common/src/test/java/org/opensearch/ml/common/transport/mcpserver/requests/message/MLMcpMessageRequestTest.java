/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.message;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

public class MLMcpMessageRequestTest {

    private final String testNodeId = "node-001";
    private final String testSessionId = "session-2023";
    private final String testRequestBody = "{ \"query\": { \"match_all\": {} } }";

    @Test
    public void testBuilderPattern() {
        MLMcpMessageRequest request = MLMcpMessageRequest
            .builder()
            .nodeId(testNodeId)
            .sessionId(testSessionId)
            .requestBody(testRequestBody)
            .build();

        assertEquals(testNodeId, request.getNodeId());
        assertEquals(testSessionId, request.getSessionId());
        assertEquals(testRequestBody, request.getRequestBody());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpMessageRequest original = buildRequest();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpMessageRequest deserialized = new MLMcpMessageRequest(input);

        assertEquals(original.getNodeId(), deserialized.getNodeId());
        assertEquals(original.getSessionId(), deserialized.getSessionId());
        assertEquals(original.getRequestBody(), deserialized.getRequestBody());
    }

    @Test
    public void testFromActionRequestSameType() {
        MLMcpMessageRequest original = buildRequest();
        MLMcpMessageRequest converted = MLMcpMessageRequest.fromActionRequest(original);
        assertSame(original, converted);
    }

    @Test
    public void testFromActionRequestDifferentType() throws IOException {
        TransportRequest transportRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                buildRequest().writeTo(out);
            }
        };

        MLMcpMessageRequest converted = MLMcpMessageRequest.fromActionRequest(transportRequest);

        assertEquals(testNodeId, converted.getNodeId());
        assertEquals(testRequestBody, converted.getRequestBody());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
        TransportRequest faultyRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("IO exception");
            }
        };
        MLMcpMessageRequest.fromActionRequest(faultyRequest);
    }

    @Test
    public void testValidationSuccess() {
        MLMcpMessageRequest request = buildRequest();
        assertNull(request.validate());
    }

    @Test(expected = IllegalStateException.class)
    public void testEmptyFieldsHandling() throws IOException {
        MLMcpMessageRequest request = MLMcpMessageRequest.builder().nodeId("").sessionId("").requestBody("").build();

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        new MLMcpMessageRequest(output.bytes().streamInput());
    }

    private MLMcpMessageRequest buildRequest() {
        return MLMcpMessageRequest.builder().nodeId(testNodeId).sessionId(testSessionId).requestBody(testRequestBody).build();
    }

}
