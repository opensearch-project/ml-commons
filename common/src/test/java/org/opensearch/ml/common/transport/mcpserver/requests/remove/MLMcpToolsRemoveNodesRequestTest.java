/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.remove;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

public class MLMcpToolsRemoveNodesRequestTest {

    private List<String> sampleTools = Arrays.asList("weather_api", "stock_predictor");
    private final String[] nodeIds = { "node-1", "node-2" };

    @Test
    public void testConstructorWithNodeIdsAndTools() {
        MLMcpToolsRemoveNodesRequest request = new MLMcpToolsRemoveNodesRequest(nodeIds, sampleTools);

        assertArrayEquals(nodeIds, request.nodesIds());
        assertEquals(2, request.getTools().size());
        assertTrue(request.getTools().contains("weather_api"));
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsRemoveNodesRequest original = new MLMcpToolsRemoveNodesRequest(nodeIds, sampleTools);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRemoveNodesRequest deserialized = new MLMcpToolsRemoveNodesRequest(input);

        assertArrayEquals(nodeIds, deserialized.nodesIds());
        assertEquals(sampleTools, deserialized.getTools());
    }

    @Test
    public void testValidateWithEmptyTools() {
        MLMcpToolsRemoveNodesRequest request = new MLMcpToolsRemoveNodesRequest(nodeIds, Collections.emptyList());

        ActionRequestValidationException exception = request.validate();
        assertNotNull(exception);
        assertEquals(1, exception.validationErrors().size());
        assertTrue(exception.validationErrors().get(0).contains("remove tools list"));
    }

    @Test
    public void testValidateWithValidTools() {
        MLMcpToolsRemoveNodesRequest request = new MLMcpToolsRemoveNodesRequest(nodeIds, sampleTools);
        assertNull(request.validate());
    }

    @Test
    public void testFromActionRequestSameType() {
        MLMcpToolsRemoveNodesRequest original = new MLMcpToolsRemoveNodesRequest(nodeIds, sampleTools);
        MLMcpToolsRemoveNodesRequest converted = MLMcpToolsRemoveNodesRequest.fromActionRequest(original);
        assertSame(original, converted);
    }

    @Test
    public void testFromActionRequestDifferentType() throws IOException {
        ActionRequest wrappedRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                new MLMcpToolsRemoveNodesRequest(nodeIds, sampleTools).writeTo(out);
            }
        };

        MLMcpToolsRemoveNodesRequest converted = MLMcpToolsRemoveNodesRequest.fromActionRequest(wrappedRequest);
        assertEquals(sampleTools, converted.getTools());
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
        MLMcpToolsRemoveNodesRequest.fromActionRequest(faultyRequest);
    }
}
