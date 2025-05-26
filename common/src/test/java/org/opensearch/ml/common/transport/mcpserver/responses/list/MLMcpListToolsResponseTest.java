/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.responses.list;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.mcpserver.requests.register.RegisterMcpTool;

public class MLMcpListToolsResponseTest {

    private List<RegisterMcpTool> sampleTools;

    @Before
    public void setUp() {
        sampleTools = List
            .of(
                new RegisterMcpTool(
                    "weather_api",
                    "weather_tool",
                    "Real-time weather data fetcher",
                    Collections.singletonMap("unit", "celsius"),
                    Collections.singletonMap("input_schema", Collections.singletonMap("type", "object")),
                    null,
                    null
                ),
                new RegisterMcpTool(
                    "stock_api",
                    "stock_tool",
                    "Stock market analyzer",
                    Collections.singletonMap("exchange", "NYSE"),
                    Collections.singletonMap("output_schema", Collections.singletonMap("format", "json")),
                    null,
                    null
                )
            );
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLMcpToolsListResponse original = new MLMcpToolsListResponse(sampleTools);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsListResponse deserialized = new MLMcpToolsListResponse(input);

        assertEquals(2, deserialized.getMcpTools().size());
        assertEquals("weather_tool", deserialized.getMcpTools().get(0).getType());
    }

    @Test
    public void testToXContentStructure() throws IOException {
        MLMcpToolsListResponse response = new MLMcpToolsListResponse(sampleTools);
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();

        assertTrue(jsonStr.contains("\"tools\":["));
        assertTrue(jsonStr.contains("\"type\":\"weather_tool\""));
        assertTrue(jsonStr.contains("\"description\":\"Stock market analyzer\""));
        assertTrue(jsonStr.contains("\"exchange\":\"NYSE\""));
    }

    @Test
    public void testEmptyToolsHandling() throws IOException {
        MLMcpToolsListResponse emptyResponse = new MLMcpToolsListResponse(Collections.emptyList());

        BytesStreamOutput output = new BytesStreamOutput();
        emptyResponse.writeTo(output);
        MLMcpToolsListResponse deserialized = new MLMcpToolsListResponse(output.bytes().streamInput());
        assertTrue(deserialized.getMcpTools().isEmpty());

        XContentBuilder builder = XContentType.JSON.contentBuilder();
        emptyResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertTrue(jsonStr.contains("\"tools\":[]"));
    }
}
