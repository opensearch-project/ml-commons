/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.CommonValue;

public class McpToolInfoTest {

    private static final String TOOL_NAME = "my_tool";
    private static final String TOOL_TYPE = "McpStreamableHttpTool";
    private static final String TOOL_DESCRIPTION = "A tool";
    private static final String SCHEMA_JSON = "\"input_schema\":\"{\"type\":\"object\",\"properties\":{\"query\":"
        + "{\"type\":\"string\",\"description\":\"The query to search for.\"},\"language\":{\"type\":\"string\""
        + ",\"description\":\"The language for the SDK to search for.\",\"detail\":{\"type\":\"string\","
        + "\"description\":\"The amount of detail to return.\"}},\"required\":[\"query\",\"language\"]";

    @Test
    public void testBuilder() {
        McpToolInfo info = McpToolInfo
            .builder()
            .name(TOOL_NAME)
            .type(TOOL_TYPE)
            .description(TOOL_DESCRIPTION)
            .inputSchema(SCHEMA_JSON)
            .build();
        assertEquals(TOOL_NAME, info.getName());
        assertEquals(TOOL_TYPE, info.getType());
        assertEquals(TOOL_DESCRIPTION, info.getDescription());
        assertEquals(SCHEMA_JSON, info.getInputSchema());
    }

    @Test
    public void testBuilder_NullInputSchema() {
        McpToolInfo info = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(TOOL_DESCRIPTION).inputSchema(null).build();
        assertNull(info.getInputSchema());
    }

    @Test
    public void testBuilder_NullType_ThrowsException() {
        NullPointerException exception = assertThrows(
            NullPointerException.class,
            () -> McpToolInfo.builder().name(TOOL_NAME).type(null).description(TOOL_DESCRIPTION).inputSchema(SCHEMA_JSON).build()
        );
        assertEquals("type cannot be null", exception.getMessage());
    }

    @Test
    public void testWriteToReadFrom() throws IOException {
        McpToolInfo original = McpToolInfo
            .builder()
            .name(TOOL_NAME)
            .type(TOOL_TYPE)
            .description(TOOL_DESCRIPTION)
            .inputSchema(SCHEMA_JSON)
            .build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        McpToolInfo parsed = new McpToolInfo(in);
        assertEquals(original.getName(), parsed.getName());
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getDescription(), parsed.getDescription());
        assertEquals(original.getInputSchema(), parsed.getInputSchema());
    }

    @Test
    public void testToXContent() throws IOException {
        McpToolInfo info = McpToolInfo
            .builder()
            .name(TOOL_NAME)
            .type(TOOL_TYPE)
            .description(TOOL_DESCRIPTION)
            .inputSchema(SCHEMA_JSON)
            .build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        info.toXContent(builder, EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"name\":\"my_tool\""));
        assertTrue(json.contains("\"type\":\"" + TOOL_TYPE + "\""));
        assertTrue(json.contains("\"description\":\"A tool\""));
        assertTrue(json.contains("\"" + CommonValue.TOOL_INPUT_SCHEMA_FIELD + "\""));
    }

    @Test
    public void testToXContent_Null_Description_AndInputSchema() throws IOException {
        McpToolInfo info = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(null).inputSchema(null).build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        info.toXContent(builder, EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"name\":\"my_tool\""));
        assertTrue(json.contains("\"type\":\"" + TOOL_TYPE + "\""));
        assertFalse(json.contains(CommonValue.TOOL_INPUT_SCHEMA_FIELD));
        assertNotNull(json);
    }
}
