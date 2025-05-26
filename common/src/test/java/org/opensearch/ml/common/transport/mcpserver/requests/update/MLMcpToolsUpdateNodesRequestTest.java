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
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.mcpserver.requests.register.MLMcpToolsRegisterNodesRequest;
import org.opensearch.search.SearchModule;

public class MLMcpToolsUpdateNodesRequestTest {

    private List<UpdateMcpTool> sampleTools;
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

    @Test(expected = IllegalArgumentException.class)
    public void testStreamSerializationWithEmptyName() throws IOException {
        UpdateMcpTool updateMcpTool = new UpdateMcpTool(
                null,
                "Updated description",
                Collections.singletonMap("parameters", "value"),
                Collections.singletonMap("attributes", "object"),
                null, null
        );
        updateMcpTool.setType("updated_tool");
        MLMcpToolsUpdateNodesRequest original = new MLMcpToolsUpdateNodesRequest(
                nodeIds,
                List.of(updateMcpTool)
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
    public void testParse_AllFields() throws Exception {
        String jsonStr = "{\n" +
                "  \"tools\": [\n" +
                "    {\n" +
                "      \"type\": \"stock_tool\",\n" +
                "      \"name\": \"stock_tool\",\n" +
                "      \"description\": \"Stock data tool\",\n" +
                "      \"parameters\": { \"exchange\": \"NYSE\" },\n" +
                "      \"attributes\": {\n" +
                "        \"input_schema\": { \"properties\": { \"symbol\": { \"type\": \"string\" } } }\n" +
                "      },\n" +
                "      \"create_time\": 1747812806243,\n" +
                "      \"last_update_time\": 1747812806243\n" +
                "    }\n" +
                "  ]\n" +
                "}\n";

        XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(
                        new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                        LoggingDeprecationHandler.INSTANCE,
                        jsonStr
                );

        MLMcpToolsUpdateNodesRequest parsed = MLMcpToolsUpdateNodesRequest.parse(parser, new String[]{"nodeId"});
        assertEquals(1, parsed.getMcpTools().size());
        assertEquals("Stock data tool", parsed.getMcpTools().get(0).getDescription());
        assertEquals(Collections.singletonMap("exchange", "NYSE"), parsed.getMcpTools().get(0).getParameters());
        assertTrue(parsed.getMcpTools().get(0).getAttributes().containsKey("input_schema"));
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
