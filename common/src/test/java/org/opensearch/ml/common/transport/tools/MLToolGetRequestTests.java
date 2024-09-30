/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.transport.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.ToolMetadata;

public class MLToolGetRequestTests {
    private List<ToolMetadata> toolMetadataList;

    @Before
    public void setUp() {
        toolMetadataList = new ArrayList<>();
        ToolMetadata wikipediaTool = ToolMetadata
            .builder()
            .name("MathTool")
            .description("Use this tool to search general knowledge on wikipedia.")
            .type("MathTool")
            .version("test")
            .build();
        toolMetadataList.add(wikipediaTool);
    }

    @Test
    public void writeTo_success() throws IOException {
        MLToolGetRequest mlToolGetRequest = MLToolGetRequest.builder().toolName("MathTool").toolMetadataList(toolMetadataList).build();

        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlToolGetRequest.writeTo(bytesStreamOutput);
        MLToolGetRequest parsedToolMetadata = new MLToolGetRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedToolMetadata.getToolName(), "MathTool");
        assertEquals(parsedToolMetadata.getToolMetadataList().get(0).getName(), toolMetadataList.get(0).getName());
    }

    @Test
    public void fromActionRequest_success() {
        MLToolGetRequest mlToolGetRequest = MLToolGetRequest.builder().toolName("MathTool").toolMetadataList(toolMetadataList).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlToolGetRequest.writeTo(out);
            }
        };
        MLToolGetRequest result = MLToolGetRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlToolGetRequest);
        assertEquals(result.getToolName(), "MathTool");
        assertEquals(result.getToolMetadataList().get(0).getName(), mlToolGetRequest.getToolMetadataList().get(0).getName());
    }

    @Test(expected = UncheckedIOException.class)
    public void fromActionRequest_IOException() {
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                throw new IOException("test");
            }
        };
        MLToolGetRequest.fromActionRequest(actionRequest);
    }

    @Test
    public void validate_Exception_NullToolName() {
        MLToolGetRequest mlToolGetRequest = MLToolGetRequest.builder().build();
        ActionRequestValidationException exception = mlToolGetRequest.validate();
        assertEquals("Validation Failed: 1: Tool name can't be null;", exception.getMessage());

    }
}
