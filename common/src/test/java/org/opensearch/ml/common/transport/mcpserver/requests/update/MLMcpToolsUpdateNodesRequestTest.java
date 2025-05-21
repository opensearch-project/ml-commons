package org.opensearch.ml.common.transport.mcpserver.requests.update;

/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */


import static org.junit.Assert.*;
import static org.opensearch.common.xcontent.json.JsonXContent.jsonXContent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;

public class MLMcpToolsUpdateNodesRequestTest {

    private List<UpdateMcpTool> sampleTools;
    private List<String> sampleBackendRoles;
    private final String[] nodeIds = { "nodeA", "nodeB" };

    @Before
    public void setup() {
        UpdateMcpTool updateMcpTool = new UpdateMcpTool(
                "updated_tool",
                "Updated description",
                Collections.singletonMap("parameters", "value"),
                Collections.singletonMap("attributes", "object"),
                null, null
        );
        updateMcpTool.setType("updated_tool");
        sampleTools = Collections.singletonList(updateMcpTool);
    }

    @Test
    public void testConstructorWithNodeIds() {
        MLMcpToolsUpdateNodesRequest request = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                sampleTools
        );

        assertArrayEquals(nodeIds, request.nodesIds());
        assertEquals("updated_tool", request.getMcpTools().get(0).getType());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsUpdateNodesRequest original = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                sampleTools
        );

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsUpdateNodesRequest deserialized = new MLMcpToolsUpdateNodesRequest(input);

        assertArrayEquals(nodeIds, deserialized.nodesIds());
        assertEquals("updated_tool", deserialized.getMcpTools().get(0).getType());
    }

    @Test(expected = UncheckedIOException.class)
    public void testStreamSerializationWithEmptyName() throws IOException {
        MLMcpToolsUpdateNodesRequest original = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                sampleTools
        );

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsUpdateNodesRequest deserialized = new MLMcpToolsUpdateNodesRequest(input);

        assertArrayEquals(nodeIds, deserialized.nodesIds());
        assertEquals("updated_tool", deserialized.getMcpTools().get(0).getType());
    }

    @Test
    public void testValidateWithEmptyTools() {
        MLMcpToolsUpdateNodesRequest request = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                Collections.emptyList()
        );

        ActionRequestValidationException validationResult = request.validate();
        assertNotNull("Should return validation error", validationResult);
        assertEquals(1, validationResult.validationErrors().size());
        assertTrue(validationResult.validationErrors().get(0).contains("tools list can not be null"));
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
                new MLMcpToolsUpdateNodesRequest(
                        nodeIds,
                        sampleTools
                ).writeTo(out);
            }
        };

        MLMcpToolsUpdateNodesRequest converted = MLMcpToolsUpdateNodesRequest.fromActionRequest(wrappedRequest);

        assertEquals("updated_tool", converted.getMcpTools().get(0).getType());
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
                throw new IOException("IO failure during update");
            }
        };

        MLMcpToolsUpdateNodesRequest.fromActionRequest(faultyRequest);
    }

    @Test
    public void testParseFromXContent() throws IOException {
        String json = "{"
                + "\"tools\":[{"
                + "\"name\":\"network_monitor\","
                + "\"description\":\"Network traffic analyzer\","
                + "\"parameters\":{\"threshold\":\"80%\"},"
                + "\"attributes\":{\"input_schema\":{\"foo\": \"bar\"}}"
                + "}]"
                + "}";

        try (XContentParser parser = jsonXContent
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json)
        ) {
            MLMcpToolsUpdateNodesRequest request = MLMcpToolsUpdateNodesRequest.parse(
                    parser,
                    new String[]{"nodeX"}
            );

            assertEquals("network_monitor", request.getMcpTools().get(0).getName());
        }
    }



    @Test
    public void testSameInstanceReturn() {
        MLMcpToolsUpdateNodesRequest request = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                sampleTools
        );
        MLMcpToolsUpdateNodesRequest converted = MLMcpToolsUpdateNodesRequest.fromActionRequest(request);
        assertSame("Should return same instance when types match", request, converted);
    }
}
