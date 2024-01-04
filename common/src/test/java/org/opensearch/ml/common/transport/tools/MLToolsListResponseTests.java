/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.tools;

import org.junit.Before;
import org.junit.Test;
// import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.ToolMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MLToolsListResponseTests {
    List<ToolMetadata> toolMetadataList;

    MLToolsListResponse mlToolsListResponse;

    @Before
    public void setUp() {
        toolMetadataList = new ArrayList<>();
        ToolMetadata searchWikipediaTool = ToolMetadata.builder()
                .name("SearchWikipediaTool")
                .description("Useful when you need to use this tool to search general knowledge on wikipedia.")
                .type("SearchWikipediaTool")
                .version(null)
                .build();
        toolMetadataList.add(searchWikipediaTool);

        mlToolsListResponse = MLToolsListResponse.builder().toolMetadata(toolMetadataList).build();
    }

    @Test
    public void writeTo_success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        MLToolsListResponse response = MLToolsListResponse.builder().toolMetadata(toolMetadataList).build();
        response.writeTo(bytesStreamOutput);
        MLToolsListResponse parsedResponse = new MLToolsListResponse(bytesStreamOutput.bytes().streamInput());
        assertNotEquals(response.toolMetadataList, parsedResponse.toolMetadataList);
        assertEquals(response.toolMetadataList.get(0).getName(), parsedResponse.toolMetadataList.get(0).getName());
        assertEquals(response.toolMetadataList.get(0).getDescription(), parsedResponse.toolMetadataList.get(0).getDescription());
    }

    @Test
    public void toXContentTest() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        mlToolsListResponse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals("{\"SearchWikipediaTool\":\"Useful when you need to use this tool to search general knowledge on wikipedia.\"}", jsonStr);
    }

    @Test
    public void fromActionResponseWithMLToolsListResponse_Success() {
        MLToolsListResponse mlToolsListResponseFromActionResponse = MLToolsListResponse.fromActionResponse(mlToolsListResponse);
        assertSame(mlToolsListResponse, mlToolsListResponseFromActionResponse);
        assertEquals(mlToolsListResponse.getToolMetadataList().get(0).getName(), mlToolsListResponseFromActionResponse.getToolMetadataList().get(0).getName());
    }

    @Test
    public void fromActionResponse_Success() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlToolsListResponse.writeTo(out);
            }
        };
        MLToolsListResponse mlToolsListResponseFromActionResponse = MLToolsListResponse.fromActionResponse(actionResponse);
        assertEquals(mlToolsListResponse.getToolMetadataList().get(0).getName(), mlToolsListResponseFromActionResponse.getToolMetadataList().get(0).getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionResponse_IOException() {
        ActionResponse actionResponse = new ActionResponse() {
            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException();
            }
        };
        MLToolsListResponse.fromActionResponse(actionResponse);
    }
}