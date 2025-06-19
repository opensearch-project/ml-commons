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

public class MLMcpRemoveNodesResponseTest {

    DiscoveryNode node1 = new DiscoveryNode(
        "node-1",
        "node-1",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    DiscoveryNode node2 = new DiscoveryNode(
        "node-2",
        "node-2",
        new TransportAddress(TransportAddress.META_ADDRESS, 9300),
        Collections.emptyMap(),
        Collections.singleton(CLUSTER_MANAGER_ROLE),
        Version.CURRENT
    );

    @Test
    public void testMultiNodeSerialization() throws IOException {
        MLMcpToolsRemoveNodesResponse original = getTestResponse();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        MLMcpToolsRemoveNodesResponse deserialized = new MLMcpToolsRemoveNodesResponse(output.bytes().streamInput());

        assertEquals(2, deserialized.getNodes().size());
        assertEquals(1, deserialized.failures().size());
    }

    @Test
    public void testResponseJsonStructure() throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        MLMcpToolsRemoveNodesResponse response = new MLMcpToolsRemoveNodesResponse(
            new ClusterName("test-cluster"),
            Arrays.asList(new MLMcpToolsRemoveNodeResponse(node1, true), new MLMcpToolsRemoveNodeResponse(node2, false)),
            Collections.singletonList(new FailedNodeException("node-3", "Not found", new IllegalArgumentException("Not found")))
        );

        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"node-1\":{\"removed\":true}"));
        assertTrue(json.contains("\"node-2\":{\"removed\":false}"));

        assertTrue(json.contains("\"not_found_exception\":\"Not found\""));
    }

    @Test
    public void testEmptyResponseHandling() throws IOException {
        MLMcpToolsRemoveNodesResponse response = new MLMcpToolsRemoveNodesResponse(
            new ClusterName("empty-cluster"),
            Collections.emptyList(),
            Collections.emptyList()
        );

        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);

        MLMcpToolsRemoveNodesResponse deserialized = new MLMcpToolsRemoveNodesResponse(output.bytes().streamInput());
        assertTrue(deserialized.getNodes().isEmpty());
        assertTrue(deserialized.failures().isEmpty());
    }

    private MLMcpToolsRemoveNodesResponse getTestResponse() {
        List<MLMcpToolsRemoveNodeResponse> nodes = Arrays
            .asList(new MLMcpToolsRemoveNodeResponse(node1, true), new MLMcpToolsRemoveNodeResponse(node2, false));

        List<FailedNodeException> failures = Collections
            .singletonList(new FailedNodeException("node-3", "Connection timeout", new IOException("Connection timeout")));

        return new MLMcpToolsRemoveNodesResponse(new ClusterName("test-cluster"), nodes, failures);
    }

    @Test
    public void testMixedStatusResponse() throws IOException {
        MLMcpToolsRemoveNodesResponse response = getTestResponse();
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"node-1\":{\"removed\":true}"));
        assertTrue(json.contains("\"node-2\":{\"removed\":false}"));

        assertTrue(json.contains("\"node-3\":{\"not_found_exception\":\"Connection timeout\"}"));
    }
}
