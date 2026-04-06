/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.agent.MLAgent;

public class MLMcpConnectorListToolsResponseTest {

    private static final String TOOL_NAME = "tool1";
    private static final String TOOL_DESCRIPTION = "description";
    private static final String TOOL_TYPE = "MCP";
    private static final McpToolInfo tool = McpToolInfo
        .builder()
        .name(TOOL_NAME)
        .type(TOOL_TYPE)
        .description(TOOL_DESCRIPTION)
        .arguments(Collections.emptyMap())
        .build();

    @Test
    public void testBuilder() {
        MLMcpConnectorListToolsResponse response = MLMcpConnectorListToolsResponse.builder().tools(List.of(tool)).build();
        assertEquals(1, response.getTools().size());
        assertEquals(TOOL_NAME, response.getTools().get(0).getName());
        assertEquals(TOOL_DESCRIPTION, response.getTools().get(0).getDescription());
        assertEquals(TOOL_TYPE, response.getTools().get(0).getType());
    }

    @Test
    public void testBuilder_NullTools() {
        MLMcpConnectorListToolsResponse response = MLMcpConnectorListToolsResponse.builder().tools(null).build();
        assertTrue(response.getTools().isEmpty());
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpConnectorListToolsResponse original = MLMcpConnectorListToolsResponse.builder().tools(List.of(tool)).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        MLMcpConnectorListToolsResponse parsed = new MLMcpConnectorListToolsResponse(out.bytes().streamInput());
        assertEquals(1, parsed.getTools().size());
        assertEquals(TOOL_NAME, parsed.getTools().get(0).getName());
    }

    @Test
    public void testStreamSerialization_EmptyList() throws IOException {
        MLMcpConnectorListToolsResponse original = MLMcpConnectorListToolsResponse.builder().tools(Collections.emptyList()).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        MLMcpConnectorListToolsResponse parsed = new MLMcpConnectorListToolsResponse(out.bytes().streamInput());
        assertTrue(parsed.getTools().isEmpty());
    }

    @Test
    public void testToXContent() throws IOException {
        MLMcpConnectorListToolsResponse response = MLMcpConnectorListToolsResponse.builder().tools(List.of(tool)).build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        response.toXContent(builder, EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.trim().startsWith("{"));
        assertTrue(json.contains("\"" + MLAgent.TOOLS_FIELD + "\":"));
        assertTrue(json.contains("\"name\":\"tool1\""));
        assertTrue(json.contains("\"type\":\"MCP\""));
    }

    @Test
    public void testFromActionResponse_SameType() {
        MLMcpConnectorListToolsResponse response = MLMcpConnectorListToolsResponse.builder().tools(Collections.emptyList()).build();
        MLMcpConnectorListToolsResponse result = MLMcpConnectorListToolsResponse.fromActionResponse(response);
        assertSame(response, result);
    }

    @Test
    public void testFromActionResponse_OtherType() {
        MLMcpConnectorListToolsResponse response = MLMcpConnectorListToolsResponse.builder().tools(List.of(tool)).build();
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                response.writeTo(out);
            }
        };
        MLMcpConnectorListToolsResponse result = MLMcpConnectorListToolsResponse.fromActionResponse(actionResponse);
        assertNotSame(response, result);
        assertEquals(1, result.getTools().size());
        assertEquals(TOOL_NAME, result.getTools().get(0).getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void testFromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLMcpConnectorListToolsResponse.fromActionResponse(actionResponse);
    }
}
