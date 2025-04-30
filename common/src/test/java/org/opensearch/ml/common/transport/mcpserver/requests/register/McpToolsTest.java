/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.register;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
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

public class McpToolsTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    private McpTools mcptools;
    private final List<McpTool> sampleTools = List
        .of(
            new McpTool(null, "weather_api", "Weather data provider", Map.of("unit", "celsius"), Map.of("type", "object")),
            new McpTool(
                null,
                "stock_api",
                "Stock market data",
                Map.of("exchange", "NYSE"),
                Map.of("properties", Map.of("symbol", Map.of("type", "string")))
            )
        );
    private final Instant createdTime = Instant.parse("2025-04-28T10:00:00Z");
    private final Instant updatedTime = Instant.parse("2025-04-28T11:30:00Z");

    @Before
    public void setUp() {
        mcptools = new McpTools(sampleTools, createdTime, updatedTime);
    }

    @Test
    public void testConstructor_Success() {
        assertEquals(2, mcptools.getTools().size());
        assertEquals(createdTime, mcptools.getCreatedTime());
        assertEquals(updatedTime, mcptools.getLastUpdateTime());
    }

    @Test
    public void testParse_FullData() throws Exception {
        String jsonStr = "{"
            + "\"tools\":[{"
            + "\"type\":\"weather_tool\","
            + "\"description\":\"Fetch weather data\","
            + "\"params\":{\"unit\":\"celsius\"},"
            + "\"schema\":{\"type\":\"object\"}"
            + "}],"
            + "\"create_time\":1745836800000,"
            + "\"last_updated_time\":1745841000000"
            + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonStr
            );
        parser.nextToken();

        McpTools parsed = McpTools.parse(parser);
        assertEquals(1, parsed.getTools().size());
        assertEquals(Instant.ofEpochMilli(1745836800000L), parsed.getCreatedTime());
        assertEquals(Instant.ofEpochMilli(1745841000000L), parsed.getLastUpdateTime());
    }

    @Test
    public void testToXContent_FullData() throws Exception {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mcptools.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertTrue(jsonStr.contains("weather_api"));
        assertTrue(jsonStr.contains("stock_api"));
    }

    @Test
    public void testStreamInputOutput_Success() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mcptools.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        McpTools parsed = new McpTools(input);

        assertEquals(2, parsed.getTools().size());
        assertEquals(createdTime, parsed.getCreatedTime());
        assertEquals(updatedTime, parsed.getLastUpdateTime());
    }

    @Test
    public void testEmptyTools() throws IOException {
        McpTools emptyTools = new McpTools(Collections.emptyList(), null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        emptyTools.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        McpTools parsed = new McpTools(input);

        assertTrue(parsed.getTools().isEmpty());
        assertNull(parsed.getCreatedTime());
        assertNull(parsed.getLastUpdateTime());
    }

    @Test
    public void testPartialData() throws Exception {
        String jsonStr = "{" + "\"tools\":[{" + "\"type\":\"minimal_tool\"" + "}]," + "\"create_time\":1745836800000" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                jsonStr
            );
        parser.nextToken();

        McpTools parsed = McpTools.parse(parser);
        assertEquals(1, parsed.getTools().size());
        assertEquals(Instant.ofEpochMilli(1745836800000L), parsed.getCreatedTime());
        assertNull(parsed.getLastUpdateTime());
    }

    @Test
    public void testInvalidDataStructure() throws Exception {
        String invalidJson = "{" + "\"create_time\":\"invalid_timestamp\"" + "}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                invalidJson
            );
        parser.nextToken();

        exceptionRule.expect(IllegalArgumentException.class);
        McpTools.parse(parser);
    }
}
