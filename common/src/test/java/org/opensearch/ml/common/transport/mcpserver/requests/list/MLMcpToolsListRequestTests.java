/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.mcpserver.requests.list;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.common.io.stream.StreamInput;

public class MLMcpToolsListRequestTests {

    @Before
    public void setUp() {}

    @Test
    public void testMLMcpToolsListRequest_withStreamInput() throws IOException {
        StreamInput streamInput = mock(StreamInput.class);
        when(streamInput.readString()).thenReturn("mockNodeId");
        MLMcpToolsListRequest mlMcpToolsListRequest = new MLMcpToolsListRequest(streamInput);
        assertNotNull(mlMcpToolsListRequest);
    }

    @Test
    public void testMLMcpToolsListRequest() {
        MLMcpToolsListRequest mlMcpToolsListRequest = new MLMcpToolsListRequest();
        assertNotNull(mlMcpToolsListRequest);
    }

    @Test
    public void testValidate() {
        MLMcpToolsListRequest mlMcpToolsListRequest = new MLMcpToolsListRequest();
        assertNull(mlMcpToolsListRequest.validate());
    }
}
