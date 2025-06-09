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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.transport.mcpserver.responses.register.MLMcpToolsRegisterNodeResponse;
import org.opensearch.ml.common.utils.StringUtils;

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
            Arrays.asList(new MLMcpToolsUpdateNodeResponse(node1, true), new MLMcpToolsUpdateNodeResponse(node2, true)),
            Collections.singletonList(new FailedNodeException("nodeC", "Timeout", new IOException("Timeout")))
        );

        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        assertTrue(json.contains("\"nodeA\":{\"updated\":true}"));
        assertTrue(json.contains("\"nodeB\":{\"updated\":true}"));
        assertTrue(json.contains("\"nodeC\":{\"error\":\"Timeout\"}"));
    }

    @Test
    public void testReadNodesFrom() throws IOException {
        List<MLMcpToolsRegisterNodeResponse> nodes = new ArrayList<>();
        MLMcpToolsRegisterNodeResponse nodeResponse = new MLMcpToolsRegisterNodeResponse(
            new DiscoveryNode(
                "foo0",
                "foo0",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
            ),
            true
        );
        nodes.add(nodeResponse);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        try (
            XContentParser parser = XContentType.JSON
                .xContent()
                .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, StringUtils.gson.toJson(nodes))
        ) {
            builder.copyCurrentStructure(parser);
            // System.out.println(BytesReference.bytes(builder).utf8ToString());
            BytesReference bytesReference = BytesReference.bytes(builder);
            try (StreamInput streamInput = bytesReference.streamInput()) {
                MLMcpToolsUpdateNodesResponse original = createTestResponse();
                List<MLMcpToolsUpdateNodeResponse> responses = original.readNodesFrom(streamInput);
                assertEquals(1, responses.size());
            }
        }
        // MLMcpToolsRegisterNodesResponse response = new MLMcpToolsRegisterNodesResponse(ClusterName.DEFAULT, nodes, null);
        // try (BytesStreamOutput streamOutput = new BytesStreamOutput()) {
        // response.writeTo(streamOutput);
        // try (StreamInput in = new BytesStreamInput(BytesReference.toBytes(streamOutput.bytes()))) {
        //
        // }
        // } catch (IOException e) {
        // fail("Unexpected error");
        // }

    }

    // private void writeTo(XContentParser parser) throws IOException {
    // ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
    // while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
    //
    // }
    // }

    private MLMcpToolsUpdateNodesResponse createTestResponse() {
        List<MLMcpToolsUpdateNodeResponse> nodes = Arrays
            .asList(new MLMcpToolsUpdateNodeResponse(node1, true), new MLMcpToolsUpdateNodeResponse(node2, false));
        List<FailedNodeException> failures = Collections
            .singletonList(new FailedNodeException("nodeX", "Connection refused", new IOException()));

        return new MLMcpToolsUpdateNodesResponse(new ClusterName("test-cluster"), nodes, failures);
    }
}
