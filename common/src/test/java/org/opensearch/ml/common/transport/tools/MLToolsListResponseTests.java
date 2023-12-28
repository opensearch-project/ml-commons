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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.ToolMetadata;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MLToolsListResponseTests {
    List<ToolMetadata> toolMetadataList;

    @Before
    public void setUp() {
        toolMetadataList = new ArrayList<>();
        ToolMetadata searchWikipediaTool = ToolMetadata.builder()
                .name("SearchWikipediaTool")
                .description("Useful when you need to use this tool to search general knowledge on wikipedia.")
                .build();
        toolMetadataList.add(searchWikipediaTool);
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
}