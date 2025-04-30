/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class McpToolTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private McpTool mcptool;
    private final String toolName = "weather_tool";
    private final String description = "Fetch weather data";
    private final Map<String, Object> params = Collections.singletonMap("unit", "celsius");
    private final Map<String, Object> schema = Collections.singletonMap("type", "object");

    @Before
    public void setUp() {
        mcptool = new McpTool(toolName, toolName, description, params, schema);
    }

    @Test
    public void testConstructor_Success() {
        assertEquals(toolName, mcptool.getType());
        assertEquals(description, mcptool.getDescription());
        assertEquals(params, mcptool.getParameters());
        assertEquals(schema, mcptool.getAttributes());
    }

    @Test
    public void testParse_AllFields() throws Exception {
        String jsonStr = "{\"type\":\"stock_tool\",\"description\":\"Stock data tool\","
            + "\"parameters\":{\"exchange\":\"NYSE\"},\"input_schema\":{\"properties\":{\"symbol\":{\"type\":\"string\"}}}}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonStr
            );
        parser.nextToken();

        McpTool parsed = McpTool.parse(parser);
        assertEquals("stock_tool", parsed.getType());
        assertEquals("Stock data tool", parsed.getDescription());
        assertEquals(Collections.singletonMap("exchange", "NYSE"), parsed.getParameters());
        assertTrue(parsed.getAttributes().containsKey("properties"));
    }

    @Test
    public void testParse_MissingNameField() throws Exception {
        String invalidJson = "{\"description\":\"Invalid tool\"}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                invalidJson
            );
        parser.nextToken();

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("type field required");
        McpTool.parse(parser);
    }

    @Test
    public void testToXContent_AllFields() throws Exception {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mcptool.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"type\":\"weather_tool\""));
        assertTrue(jsonStr.contains("\"description\":\"Fetch weather data\""));
        assertTrue(jsonStr.contains("\"parameters\":{\"unit\":\"celsius\"}"));
        assertTrue(jsonStr.contains("\"input_schema\":{\"type\":\"object\"}"));
    }

    @Test
    public void testToXContent_MinimalFields() throws Exception {
        McpTool minimalTool = new McpTool(null, "minimal_tool", null, null, null);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        minimalTool.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"type\":\"minimal_tool\""));
        assertFalse(jsonStr.contains("description"));
        assertFalse(jsonStr.contains("params"));
        assertFalse(jsonStr.contains("schema"));
    }

    @Test
    public void testStreamInputOutput_Success() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mcptool.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        McpTool parsed = new McpTool(input);

        assertEquals(toolName, parsed.getType());
        assertEquals(description, parsed.getDescription());
        assertEquals(params, parsed.getParameters());
        assertEquals(schema, parsed.getAttributes());
    }

    @Test
    public void testStreamInputOutput_WithNullFields() throws IOException {
        McpTool toolWithNulls = new McpTool(null, "null_tool", null, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        toolWithNulls.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        McpTool parsed = new McpTool(input);

        assertEquals("null_tool", parsed.getType());
        assertNull(parsed.getDescription());
        assertNull(parsed.getParameters());
        assertNull(parsed.getAttributes());
    }

    @Test
    public void testComplexParameters() throws Exception {
        Map<String, Object> complexParams = new HashMap<>();
        complexParams.put("config", Collections.singletonMap("timeout", 30));

        Map<String, Object> complexSchema = new HashMap<>();
        complexSchema.put("type", "object");
        complexSchema.put("properties", Collections.singletonMap("location", Collections.singletonMap("type", "string")));

        McpTool complexTool = new McpTool(null, "complex_tool", null, complexParams, complexSchema);

        BytesStreamOutput output = new BytesStreamOutput();
        complexTool.writeTo(output);
        McpTool parsed = new McpTool(output.bytes().streamInput());

        assertEquals(complexParams, parsed.getParameters());
        assertEquals(complexSchema, parsed.getAttributes());

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        complexTool.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertTrue(jsonStr.contains("\"config\":{\"timeout\":30}"));
        assertTrue(jsonStr.contains("\"properties\":{\"location\":{\"type\":\"string\"}}"));
    }
}
