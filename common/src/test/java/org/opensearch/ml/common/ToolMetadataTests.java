/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

public class ToolMetadataTests {
    ToolMetadata toolMetadata;

    Function<XContentParser, ToolMetadata> function;

    @Before
    public void setUp() {
        toolMetadata = ToolMetadata.builder()
                .name("MathTool")
                .description("Use this tool to calculate any math problem.")
                .type("MathTool")
                .version("test")
                .build();

        function = parser -> {
            try {
                return ToolMetadata.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    public void toXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        toolMetadata.toXContent(builder, EMPTY_PARAMS);
        String toolMetadataString = TestHelper.xContentBuilderToString(builder);
        assertEquals(toolMetadataString, "{\"name\":\"MathTool\",\"description\":\"Use this tool to calculate any math problem.\",\"type\":\"MathTool\",\"version\":\"test\"}");
    }

    @Test
    public void toXContent_nullValue() throws IOException {
        ToolMetadata emptyToolMetadata = ToolMetadata.builder().build();
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        emptyToolMetadata.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String toolMetadataString = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"version\":\"undefined\"}", toolMetadataString);
    }

    @Test
    public void parse() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        toolMetadata.toXContent(builder, EMPTY_PARAMS);
        String toolMetadataString = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = XContentType.JSON.xContent().createParser(NamedXContentRegistry.EMPTY,
                LoggingDeprecationHandler.INSTANCE, toolMetadataString);
        parser.nextToken();
        toolMetadata.equals(function.apply(parser));
    }


    @Test
    public void readInputStream_Success() throws IOException {
        readInputStream(toolMetadata);
    }

    private void readInputStream(ToolMetadata toolMetadata) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        toolMetadata.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        ToolMetadata parsedToolMetadata = new ToolMetadata(streamInput);
        assertEquals(toolMetadata.getName(), parsedToolMetadata.getName());
        assertEquals(toolMetadata.getDescription(), parsedToolMetadata.getDescription());
        assertEquals(toolMetadata.getType(), parsedToolMetadata.getType());
        assertEquals(toolMetadata.getVersion(), parsedToolMetadata.getVersion());
    }
}
