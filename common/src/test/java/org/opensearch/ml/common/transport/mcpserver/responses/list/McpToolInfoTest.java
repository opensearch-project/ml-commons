/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;

public class McpToolInfoTest {

    private static final String TOOL_NAME = "my_tool";
    private static final String TOOL_DESCRIPTION = "A tool";
    private static final String TOOL_TYPE = "type1";

    @Test
    public void testBuilder() {
        Map<String, String> args = new HashMap<>();
        args.put("query", "string");
        args.put("max_limit", "long");
        McpToolInfo info = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(TOOL_DESCRIPTION).arguments(args).build();
        assertEquals(TOOL_NAME, info.getName());
        assertEquals(TOOL_TYPE, info.getType());
        assertEquals(TOOL_DESCRIPTION, info.getDescription());
        assertEquals(args, info.getArguments());
        assertEquals(2, info.getArguments().size());
    }

    @Test
    public void testBuilder_NullArguments() {
        McpToolInfo info = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(TOOL_DESCRIPTION).arguments(null).build();
        assertNotNull(info.getArguments());
        assertTrue(info.getArguments().isEmpty());
    }

    @Test
    public void testWriteToReadFrom() throws IOException {
        Map<String, String> args = Collections.singletonMap("k", "v");
        McpToolInfo original = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(TOOL_DESCRIPTION).arguments(args).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        McpToolInfo parsed = new McpToolInfo(in);
        assertEquals(original.getName(), parsed.getName());
        assertEquals(original.getType(), parsed.getType());
        assertEquals(original.getDescription(), parsed.getDescription());
        assertEquals(original.getArguments(), parsed.getArguments());
    }

    @Test
    public void testToXContent() throws IOException {
        Map<String, String> args = Collections.singletonMap("arg1", "string");
        McpToolInfo info = McpToolInfo.builder().name(TOOL_NAME).type(TOOL_TYPE).description(TOOL_DESCRIPTION).arguments(args).build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        info.toXContent(builder, EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"name\":\"my_tool\""));
        assertTrue(json.contains("\"type\":\"type1\""));
        assertTrue(json.contains("\"description\":\"A tool\""));
        assertTrue(json.contains("\"arguments\""));
        assertTrue(json.contains("\"arg1\""));
    }

    @Test
    public void testToXContent_NullDescription() throws IOException {
        McpToolInfo info = McpToolInfo
            .builder()
            .name(TOOL_NAME)
            .type(TOOL_TYPE)
            .description(null)
            .arguments(Collections.emptyMap())
            .build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        info.toXContent(builder, EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"name\":\"my_tool\""));
        assertTrue(json.contains("\"type\":\"type1\""));
        assertTrue(!json.contains("\"description\"") || json.contains("\"arguments\":{}"));
    }
}
