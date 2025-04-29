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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class MLMcpRegisterNodesResponseTest {

    DiscoveryNode node1 = new DiscoveryNode(
        "foo1",
        "foo1",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    DiscoveryNode node2 = new DiscoveryNode(
        "foo2",
        "foo2",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    @Test
    public void testMultiNodeSerialization() throws IOException {
        MLMcpRegisterNodesResponse original = getMlMcpRegisterNodesResponse();
        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpRegisterNodesResponse deserialized = new MLMcpRegisterNodesResponse(output.bytes().streamInput());

        assertEquals(2, deserialized.getNodes().size());
        assertEquals(1, deserialized.failures().size());
    }

    @Test
    public void testResponseJsonStructure() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        MLMcpRegisterNodesResponse response = new MLMcpRegisterNodesResponse(
            new ClusterName("test"),
            Collections.singletonList(new MLMcpRegisterNodeResponse(node1, true)),
            Collections.singletonList(new FailedNodeException("foo2", "Connection timeout", new IOException()))
        );

        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();
        assertTrue(json.contains("\"foo1\":{\"created\":true}"));
    }

    private MLMcpRegisterNodesResponse getMlMcpRegisterNodesResponse() {
        List<MLMcpRegisterNodeResponse> nodes = Arrays
            .asList(new MLMcpRegisterNodeResponse(node1, true), new MLMcpRegisterNodeResponse(node2, false));
        List<FailedNodeException> failures = Collections
            .singletonList(new FailedNodeException("node3", "Connection timeout", new IOException()));

        return new MLMcpRegisterNodesResponse(new ClusterName("test-cluster"), nodes, failures);
    }
}
