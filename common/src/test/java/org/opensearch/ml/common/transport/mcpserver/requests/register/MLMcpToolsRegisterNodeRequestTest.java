/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

public class MLMcpToolsRegisterNodeRequestTest {

    private McpTools sampleTools;
    private final Instant timestamp = Instant.parse("2025-04-28T08:30:00Z");

    @Before
    public void setUp() {
        sampleTools = new McpTools(
            Collections
                .singletonList(
                    new McpTool(
                        null,
                        "test_tool",
                        "Sample tool",
                        Collections.singletonMap("param", "value"),
                        Collections.singletonMap("type", "object")
                    )
                ),
            timestamp,
            timestamp.plusSeconds(3600)
        );
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsRegisterNodeRequest originalRequest = new MLMcpToolsRegisterNodeRequest(sampleTools);

        BytesStreamOutput output = new BytesStreamOutput();
        originalRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRegisterNodeRequest deserializedRequest = new MLMcpToolsRegisterNodeRequest(input);

        assertEquals(1, deserializedRequest.getMcpTools().getTools().size());
        assertEquals(timestamp, deserializedRequest.getMcpTools().getCreatedTime());
        assertEquals("test_tool", deserializedRequest.getMcpTools().getTools().get(0).getType());
    }

    @Test
    public void testFromActionRequest_SameType() {
        MLMcpToolsRegisterNodeRequest original = new MLMcpToolsRegisterNodeRequest(sampleTools);
        MLMcpToolsRegisterNodeRequest converted = MLMcpToolsRegisterNodeRequest.fromActionRequest(original);

        assertSame("Should return same instance for matching types", original, converted);
    }

    @Test
    public void testFromActionRequest_DifferentType() throws IOException {
        TransportRequest transportRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                new MLMcpToolsRegisterNodeRequest(sampleTools).writeTo(out);
            }
        };

        MLMcpToolsRegisterNodeRequest result = MLMcpToolsRegisterNodeRequest.fromActionRequest(transportRequest);

        assertNotNull("Converted request should not be null", result);
        assertEquals("test_tool", result.getMcpTools().getTools().get(0).getType());
        assertEquals(timestamp, result.getMcpTools().getCreatedTime());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequest_IOException() {
        TransportRequest faultyRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("IO failure");
            }
        };

        MLMcpToolsRegisterNodeRequest.fromActionRequest(faultyRequest);
    }

    @Test
    public void testValidateMethod() {
        MLMcpToolsRegisterNodeRequest request = new MLMcpToolsRegisterNodeRequest(sampleTools);
        assertTrue("Validation should always pass", request.validate() == null);
    }

    @Test
    public void testEmptyToolsHandling() throws IOException {
        McpTools emptyTools = new McpTools(Collections.emptyList(), null, null);
        MLMcpToolsRegisterNodeRequest request = new MLMcpToolsRegisterNodeRequest(emptyTools);

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        MLMcpToolsRegisterNodeRequest result = new MLMcpToolsRegisterNodeRequest(output.bytes().streamInput());

        assertTrue("Should preserve empty tools list", result.getMcpTools().getTools().isEmpty());
        assertNull("Should preserve null create time", result.getMcpTools().getCreatedTime());
    }
}
