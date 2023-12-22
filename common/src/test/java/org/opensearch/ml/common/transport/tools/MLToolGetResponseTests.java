package org.opensearch.ml.common.transport.tools;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.ToolMetadata;

import java.io.IOException;

import static org.junit.Assert.*;

public class MLToolGetResponseTests {
    ToolMetadata toolMetadata;

    @Before
    public void setUp() {
        toolMetadata = ToolMetadata.builder()
                .name("MathTool")
                .description("Use this tool to calculate any math problem.")
                .build();
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

}
