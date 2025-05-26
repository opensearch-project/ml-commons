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

public class MLMcpToolsUpdateNodesResponseTest {

    private final DiscoveryNode node1 = new DiscoveryNode(
            "nodeA",
            "nodeA",
            new TransportAddress(TransportAddress.META_ADDRESS, 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
    );

    private final DiscoveryNode node2 = new DiscoveryNode(
            "nodeB",
            "nodeB",
            new TransportAddress(TransportAddress.META_ADDRESS, 9300),
            Collections.emptyMap(),
            Collections.singleton(DATA_ROLE),
            Version.CURRENT
    );

    @Test
    public void testMultiNodeSerialization() throws IOException {
        MLMcpToolsUpdateNodesResponse original = createTestResponse();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsUpdateNodesResponse deserialized = new MLMcpToolsUpdateNodesResponse(output.bytes().streamInput());

        assertEquals(2, deserialized.getNodes().size());
        assertEquals(1, deserialized.failures().size());
        assertTrue(deserialized.getNodes().get(0).getUpdated());
    }

    @Test
    public void testResponseJsonStructure() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        MLMcpToolsUpdateNodesResponse response = new MLMcpToolsUpdateNodesResponse(
                new ClusterName("prod-cluster"),
                Arrays.asList(
                        new MLMcpToolsUpdateNodeResponse(node1, true),
                        new MLMcpToolsUpdateNodeResponse(node2, true)
                ),
                Collections.singletonList(new FailedNodeException("nodeC", "Timeout", new IOException("Timeout")))
        );

        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"nodeA\":{\"updated\":true}"));
        assertTrue(json.contains("\"nodeB\":{\"updated\":true}"));
        assertTrue(json.contains("\"nodeC\":{\"error\":\"Timeout\"}"));
    }

    private MLMcpToolsUpdateNodesResponse createTestResponse() {
        List<MLMcpToolsUpdateNodeResponse> nodes = Arrays.asList(
                new MLMcpToolsUpdateNodeResponse(node1, true),
                new MLMcpToolsUpdateNodeResponse(node2, false)
        );
        List<FailedNodeException> failures = Collections.singletonList(
                new FailedNodeException("nodeX", "Connection refused", new IOException())
        );

        return new MLMcpToolsUpdateNodesResponse(
                new ClusterName("test-cluster"),
                nodes,
                failures
        );
    }
}
