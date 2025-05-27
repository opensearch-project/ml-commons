/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.responses.remove;

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

public class MLMcpRemoveNodeResponseTest {

    DiscoveryNode node = new DiscoveryNode(
        "node-1",
        "n1",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    @Test
    public void testSerialization() throws IOException {
        MLMcpRemoveNodeResponse original = new MLMcpRemoveNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpRemoveNodeResponse deserialized = new MLMcpRemoveNodeResponse(output.bytes().streamInput());

        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getDeleted());

        MLMcpRemoveNodeResponse originalFalse = new MLMcpRemoveNodeResponse(node, false);
        output = new BytesStreamOutput();
        originalFalse.writeTo(output);

        MLMcpRemoveNodeResponse deserializedFalse = new MLMcpRemoveNodeResponse(output.bytes().streamInput());
        assertFalse(deserializedFalse.getDeleted());
    }

    @Test
    public void testXContentGeneration() throws IOException {
        MLMcpRemoveNodeResponse response = new MLMcpRemoveNodeResponse(node, true);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);

        String json = builder.toString();
        assertTrue(json.contains("\"deleted\":true"));

        MLMcpRemoveNodeResponse responseFalse = new MLMcpRemoveNodeResponse(node, false);
        builder = XContentFactory.jsonBuilder();
        responseFalse.toXContent(builder, ToXContent.EMPTY_PARAMS);
        json = builder.toString();
        assertTrue(json.contains("\"deleted\":false"));
    }

    @Test
    public void testReadResponse() throws IOException {
        MLMcpRemoveNodeResponse original = new MLMcpRemoveNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpRemoveNodeResponse deserialized = MLMcpRemoveNodeResponse.readResponse(output.bytes().streamInput());

        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getDeleted());
    }

}
