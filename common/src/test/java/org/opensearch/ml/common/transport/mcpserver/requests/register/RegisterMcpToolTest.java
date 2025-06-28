/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
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

public class RegisterMcpToolTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private McpToolRegisterInput mcptool;
    private final String toolName = "weather_tool";
    private final String description = "Fetch weather data";
    private final Map<String, Object> params = Collections.singletonMap("unit", "celsius");
    private final Map<String, Object> attributes = Collections.singletonMap("type", "object");

    @Before
    public void setUp() {
        mcptool = new McpToolRegisterInput(toolName, toolName, description, params, attributes, Instant.now(), Instant.now());
    }

    @Test
    public void testConstructor_Success() {
        assertEquals(toolName, mcptool.getType());
        assertEquals(description, mcptool.getDescription());
        assertEquals(params, mcptool.getParameters());
        assertEquals(attributes, mcptool.getAttributes());
    }

    @Test
    public void testParse_AllFields() throws Exception {
        String jsonStr = "{\n"
            + "  \"type\": \"stock_tool\",\n"
            + "  \"description\": \"Stock data tool\",\n"
            + "  \"parameters\": { \"exchange\": \"NYSE\" },\n"
            + "  \"attributes\": {\n"
            + "    \"input_schema\": { \"properties\": { \"symbol\": { \"type\": \"string\" } } }\n"
            + "  },\n"
            + "  \"create_time\": 1747812806243,\n"
            + "  \"last_update_time\": 1747812806243\n"
            + "}\n";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonStr
            );
        parser.nextToken();

        McpToolRegisterInput parsed = McpToolRegisterInput.parse(parser);
        assertEquals("stock_tool", parsed.getType());
        assertEquals("Stock data tool", parsed.getDescription());
        assertEquals(Collections.singletonMap("exchange", "NYSE"), parsed.getParameters());
        assertTrue(parsed.getAttributes().containsKey("input_schema"));
    }

    @Test
    public void testParse_MissingTypeField() throws Exception {
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
        McpToolRegisterInput.parse(parser);
    }

    @Test
    public void testToXContent_AllFields() throws Exception {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mcptool.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"type\":\"weather_tool\""));
        assertTrue(jsonStr.contains("\"description\":\"Fetch weather data\""));
        assertTrue(jsonStr.contains("\"parameters\":{\"unit\":\"celsius\"}"));
        assertTrue(jsonStr.contains("\"attributes\":{\"type\":\"object\"}"));
    }

    @Test
    public void testToXContent_MinimalFields() throws Exception {
        McpToolRegisterInput minimalTool = new McpToolRegisterInput(null, "minimal_tool", null, null, null, null, null);
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
        McpToolRegisterInput parsed = new McpToolRegisterInput(input);

        assertEquals(toolName, parsed.getType());
        assertEquals(description, parsed.getDescription());
        assertEquals(params, parsed.getParameters());
        assertEquals(attributes, parsed.getAttributes());
    }

    @Test
    public void testStreamInputOutput_WithNullFields() throws IOException {
        McpToolRegisterInput toolWithNulls = new McpToolRegisterInput(null, "null_tool", null, null, null, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        toolWithNulls.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        McpToolRegisterInput parsed = new McpToolRegisterInput(input);

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

        McpToolRegisterInput complexTool = new McpToolRegisterInput(null, "complex_tool", null, complexParams, complexSchema, null, null);

        BytesStreamOutput output = new BytesStreamOutput();
        complexTool.writeTo(output);
        McpToolRegisterInput parsed = new McpToolRegisterInput(output.bytes().streamInput());

        assertEquals(complexParams, parsed.getParameters());
        assertEquals(complexSchema, parsed.getAttributes());

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        complexTool.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertTrue(jsonStr.contains("\"config\":{\"timeout\":30}"));
        assertTrue(jsonStr.contains("\"properties\":{\"location\":{\"type\":\"string\"}}"));
    }
}
