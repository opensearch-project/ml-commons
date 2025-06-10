/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.responses.update;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.*;

import java.io.IOException;
import java.util.Collections;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLMcpToolsUpdateNodeResponseTest {

    private final DiscoveryNode node = new DiscoveryNode(
        "node-01",
        "node-01",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    @Test
    public void testSerialization() throws IOException {
        MLMcpToolsUpdateNodeResponse original = new MLMcpToolsUpdateNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsUpdateNodeResponse deserialized = new MLMcpToolsUpdateNodeResponse(output.bytes().streamInput());
        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getUpdated());
    }

    @Test
    public void testXContentGeneration() throws IOException {
        MLMcpToolsUpdateNodeResponse response = new MLMcpToolsUpdateNodeResponse(node, false);

        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"node-01\":false"));
        assertTrue(json.startsWith("{") && json.endsWith("}"));
    }

    @Test
    public void testReadResponse() throws IOException {
        MLMcpToolsUpdateNodeResponse original = new MLMcpToolsUpdateNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsUpdateNodeResponse deserialized = MLMcpToolsUpdateNodeResponse.readResponse(output.bytes().streamInput());
        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getUpdated());
    }
}
