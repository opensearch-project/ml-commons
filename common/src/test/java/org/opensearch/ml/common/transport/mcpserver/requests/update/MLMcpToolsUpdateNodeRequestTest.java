/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.InputStreamStreamInput;
import org.opensearch.core.common.io.stream.OutputStreamStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.transport.TransportRequest;

public class MLMcpToolsUpdateNodeRequestTest {

    private List<UpdateMcpTool> sampleTools;

    @Before
    public void setUp() {
        UpdateMcpTool updateMcpTool = new UpdateMcpTool(
            "updated_tool",
            "Updated description",
            Collections.singletonMap("parameters", "value"),
            Collections.singletonMap("attributes", "object"),
            null,
            null
        );
        updateMcpTool.setType("updated_tool");
        sampleTools = Collections.singletonList(updateMcpTool);
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsUpdateNodeRequest originalRequest = new MLMcpToolsUpdateNodeRequest(sampleTools);

        BytesStreamOutput output = new BytesStreamOutput();
        originalRequest.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsUpdateNodeRequest deserializedRequest = new MLMcpToolsUpdateNodeRequest(input);

        assertEquals(1, deserializedRequest.getMcpTools().size());
        assertEquals("updated_tool", deserializedRequest.getMcpTools().get(0).getType());
    }

    @Test
    public void testFromActionRequest_SameType() {
        MLMcpToolsUpdateNodeRequest original = new MLMcpToolsUpdateNodeRequest(sampleTools);
        MLMcpToolsUpdateNodeRequest converted = MLMcpToolsUpdateNodeRequest.fromActionRequest(original);

        assertSame("Should return same instance for matching types", original, converted);
    }

    @Test
    public void testFromActionRequest_DifferentType() throws IOException {
        TransportRequest transportRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                new MLMcpToolsUpdateNodeRequest(sampleTools).writeTo(out);
            }
        };

        MLMcpToolsUpdateNodeRequest result = MLMcpToolsUpdateNodeRequest.fromActionRequest(transportRequest);

        assertNotNull("Converted request should not be null", result);
        assertEquals("updated_tool", result.getMcpTools().get(0).getType());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionRequest_IOException() {
        TransportRequest faultyRequest = new TransportRequest() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("IO failure during update");
            }
        };

        MLMcpToolsUpdateNodeRequest.fromActionRequest(faultyRequest);
    }

    @Test
    public void testValidateMethod() {
        MLMcpToolsUpdateNodeRequest request = new MLMcpToolsUpdateNodeRequest(sampleTools);
        ActionRequestValidationException validationResult = request.validate();
        assertTrue("Validation should return null for valid request", validationResult == null);
    }

    @Test
    public void testEmptyToolsHandling() throws IOException {
        List<UpdateMcpTool> emptyTools = Collections.emptyList();
        MLMcpToolsUpdateNodeRequest request = new MLMcpToolsUpdateNodeRequest(emptyTools);

        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        MLMcpToolsUpdateNodeRequest result = new MLMcpToolsUpdateNodeRequest(output.bytes().streamInput());

        assertTrue("Should preserve empty tools list", result.getMcpTools().isEmpty());
    }

    @Test
    public void testStreamImplementationDetails() throws IOException {
        MLMcpToolsUpdateNodeRequest request = new MLMcpToolsUpdateNodeRequest(sampleTools);

        BytesStreamOutput baos = new BytesStreamOutput();
        try (OutputStreamStreamOutput osso = new OutputStreamStreamOutput(baos)) {
            request.writeTo(osso);
        }

        try (InputStreamStreamInput input = new InputStreamStreamInput(baos.bytes().streamInput())) {
            MLMcpToolsUpdateNodeRequest reconstructed = new MLMcpToolsUpdateNodeRequest(input);
            assertEquals("Should maintain tool count through stream", request.getMcpTools().size(), reconstructed.getMcpTools().size());
        }
    }
}
