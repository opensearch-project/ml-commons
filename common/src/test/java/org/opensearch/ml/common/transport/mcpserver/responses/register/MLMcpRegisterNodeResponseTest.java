/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common.transport.mcpserver.responses.register;

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

public class MLMcpRegisterNodeResponseTest {

    DiscoveryNode node = new DiscoveryNode(
        "foo1",
        "foo1",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    @Test
    public void testSerialization() throws IOException {
        MLMcpToolsRegisterNodeResponse original = new MLMcpToolsRegisterNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsRegisterNodeResponse deserialized = new MLMcpToolsRegisterNodeResponse(output.bytes().streamInput());

        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getCreated());
    }

    @Test
    public void testXContentGeneration() throws IOException {
        MLMcpToolsRegisterNodeResponse response = new MLMcpToolsRegisterNodeResponse(node, true);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);

        String json = builder.toString();
        assertNotNull(json);
    }

    @Test
    public void testReadResponse() throws IOException {
        MLMcpToolsRegisterNodeResponse original = new MLMcpToolsRegisterNodeResponse(node, true);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsRegisterNodeResponse deserialized = MLMcpToolsRegisterNodeResponse.readResponse(output.bytes().streamInput());

        assertEquals(node.getId(), deserialized.getNode().getId());
        assertTrue(deserialized.getCreated());
    }
}
