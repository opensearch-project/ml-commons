/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.requests.remove;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLMcpToolsRemoveNodeRequestTest {
    private final List<String> sampleTools = List.of("weather_api", "stock_analyzer");

    @Test
    public void testConstructorWithToolList() {
        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(sampleTools);
        assertEquals(2, request.getMcpTools().size());
        assertTrue(request.getMcpTools().contains("weather_api"));
    }

    @Test
    public void testStreamSerialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        MLMcpToolsRemoveNodeRequest original = new MLMcpToolsRemoveNodeRequest(sampleTools);
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRemoveNodeRequest deserialized = new MLMcpToolsRemoveNodeRequest(input);

        assertEquals(sampleTools, deserialized.getMcpTools());
    }

    @Test
    public void testEmptyToolsHandling() throws IOException {
        MLMcpToolsRemoveNodeRequest request = new MLMcpToolsRemoveNodeRequest(Collections.emptyList());
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRemoveNodeRequest result = new MLMcpToolsRemoveNodeRequest(input);

        assertTrue(result.getMcpTools().isEmpty());
    }

    @Test
    public void testNullToolsList() throws IOException {
        MLMcpToolsRemoveNodeRequest request = MLMcpToolsRemoveNodeRequest.builder().tools(null).build();
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLMcpToolsRemoveNodeRequest result = new MLMcpToolsRemoveNodeRequest(input);

        assertNull(result.getMcpTools());
    }

}
