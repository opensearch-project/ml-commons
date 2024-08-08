/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.tools;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.ToolMetadata;

public class MLToolGetResponseTests {
    ToolMetadata toolMetadata;

    MLToolGetResponse mlToolGetResponse;

    @Before
    public void setUp() {
        toolMetadata = ToolMetadata
            .builder()
            .name("MathTool")
            .description("Use this tool to calculate any math problem.")
            .type("MathTool")
            .version(null)
            .build();

        mlToolGetResponse = MLToolGetResponse.builder().toolMetadata(toolMetadata).build();
    }

    @Test
    public void writeTo_success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLToolGetResponse response = MLToolGetResponse.builder().toolMetadata(toolMetadata).build();
        response.writeTo(bytesStreamOutput);
        MLToolGetResponse parsedResponse = new MLToolGetResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.toolMetadata, parsedResponse.toolMetadata);
        assertEquals(response.toolMetadata.getName(), parsedResponse.getToolMetadata().getName());
    }

    @Test
    public void toXContentTest() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlToolGetResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(
            "{\"name\":\"MathTool\",\"description\":\"Use this tool to calculate any math problem.\",\"type\":\"MathTool\",\"version\":\"undefined\"}",
            jsonStr
        );
    }

    @Test
    public void fromActionResponseWithMLToolGetResponse_Success() {
        MLToolGetResponse mlToolGetResponseFromActionResponse = MLToolGetResponse.fromActionResponse(mlToolGetResponse);
        assertSame(mlToolGetResponse, mlToolGetResponseFromActionResponse);
        assertEquals(mlToolGetResponse.getToolMetadata().getName(), mlToolGetResponseFromActionResponse.getToolMetadata().getName());
    }

    @Test
    public void fromActionResponse_Success() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlToolGetResponse.writeTo(out);
            }
        };
        MLToolGetResponse mlToolGetResponseFromActionResponse = MLToolGetResponse.fromActionResponse(actionResponse);
        assertEquals(mlToolGetResponse.getToolMetadata().getName(), mlToolGetResponseFromActionResponse.getToolMetadata().getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLToolGetResponse.fromActionResponse(actionResponse);
    }
}
