/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLMcpToolsRegisterNodesRequestTest {

    private List<RegisterMcpTool> sampleTools;
    private final String[] nodeIds = { "node1", "node2" };

    @Before
    public void setup() {
        sampleTools = List
            .of(
                new RegisterMcpTool(null, "metric_analyzer", "System monitoring tool", Map.of("interval", "60s"), Map.of("type", "object"), null, null)
            );
    }

    @Test
    public void testConstructorWithNodeIds() {
        MLMcpToolsRegisterNodesRequest request = new MLMcpToolsRegisterNodesRequest(nodeIds, sampleTools);

        assertArrayEquals(nodeIds, request.nodesIds());
        assertEquals(1, request.getMcpTools().size());
        assertEquals("metric_analyzer", request.getMcpTools().get(0).getType());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsRegisterNodesRequest original = new MLMcpToolsRegisterNodesRequest(nodeIds, sampleTools);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRegisterNodesRequest deserialized = new MLMcpToolsRegisterNodesRequest(input);

        assertArrayEquals(nodeIds, deserialized.nodesIds());
        assertEquals("metric_analyzer", deserialized.getMcpTools().get(0).getType());
    }

    @Test
    public void testValidateWithEmptyTools() {
        MLMcpToolsRegisterNodesRequest request = new MLMcpToolsRegisterNodesRequest(nodeIds, Collections.emptyList());

        assertNotNull("Should return validation error", request.validate());
        assertEquals(1, request.validate().validationErrors().size());
        assertTrue(request.validate().validationErrors().get(0).contains("tools list can not be null"));
    }

    @Test
    public void testFromActionRequestWithDifferentType() throws IOException {
        ActionRequest wrappedRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                new MLMcpToolsRegisterNodesRequest(nodeIds, sampleTools).writeTo(out);
            }
        };

        MLMcpToolsRegisterNodesRequest converted = MLMcpToolsRegisterNodesRequest.fromActionRequest(wrappedRequest);

        assertEquals("metric_analyzer", converted.getMcpTools().get(0).getType());
        assertArrayEquals(nodeIds, converted.nodesIds());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequestIOException() {
        ActionRequest faultyRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("IO failure");
            }
        };

        MLMcpToolsRegisterNodesRequest.fromActionRequest(faultyRequest);
    }
}
