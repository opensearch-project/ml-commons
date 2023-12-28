/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.tools;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionRequestValidationException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.ml.common.ToolMetadata;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class MLToolsListRequestTests {
    private List<ToolMetadata> externalTools;

    @Before
    public void setUp() {
        externalTools = new ArrayList<>();
        ToolMetadata wikipediaTool = ToolMetadata.builder()
                .name("WikipediaTool")
                .description("Use this tool to search general knowledge on wikipedia.")
                .build();
        externalTools.add(wikipediaTool);
    }
    @Test
    public void writeTo_success() throws IOException {

        MLToolsListRequest mlToolsListRequest = MLToolsListRequest.builder()
                .toolFactories(externalTools)
                .build();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        mlToolsListRequest.writeTo(bytesStreamOutput);
        MLToolsListRequest parsedToolMetadata = new MLToolsListRequest(bytesStreamOutput.bytes().streamInput());
        assertEquals(parsedToolMetadata.getToolFactories().get(0).getName(), externalTools.get(0).getName());
        assertEquals(parsedToolMetadata.getToolFactories().get(0).getDescription(), externalTools.get(0).getDescription());
    }

    @Test
    public void fromActionRequest_success() {
        MLToolsListRequest mlToolsListRequest = MLToolsListRequest.builder().toolFactories(externalTools).build();
        ActionRequest actionRequest = new ActionRequest() {
            @Override
            public ActionRequestValidationException validate() {
                return null;
            }

            @Override
            public void writeTo(StreamOutput out) throws IOException {
                mlToolsListRequest.writeTo(out);
            }
        };
        MLToolsListRequest result = MLToolsListRequest.fromActionRequest(actionRequest);
        assertNotSame(result, mlToolsListRequest);
        assertEquals(result.getToolFactories().get(0).getName(), mlToolsListRequest.getToolFactories().get(0).getName());
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
        MLToolsListRequest.fromActionRequest(actionRequest);
    }
}