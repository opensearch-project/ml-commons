/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.transport.TransportAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class MLInPlaceUpdateModelNodesRequestTest {

    @Test
    public void testConstructorSerialization1() throws IOException {
        String modelId = "testModelId";
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        MLInPlaceUpdateModelNodeRequest inPlaceUpdateModelNodeRequest = new  MLInPlaceUpdateModelNodeRequest(
                new  MLInPlaceUpdateModelNodesRequest(nodeIds, modelId, true)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        inPlaceUpdateModelNodeRequest.writeTo(output);
        assertEquals("testModelId", inPlaceUpdateModelNodeRequest.getMlInPlaceUpdateModelNodesRequest().getModelId());
    }

    @Test
    public void testConstructorSerialization2() {
        String modelId = "testModelId";
        DiscoveryNode localNode1 = new DiscoveryNode(
                "foo1",
                "foo1",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        DiscoveryNode localNode2 = new DiscoveryNode(
                "foo2",
                "foo2",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        DiscoveryNode[] nodes = {localNode1, localNode2};
        MLInPlaceUpdateModelNodeRequest inPlaceUpdateModelNodeRequest = new  MLInPlaceUpdateModelNodeRequest(
                new MLInPlaceUpdateModelNodesRequest(nodes, modelId, true)
        );
        assertEquals(2, inPlaceUpdateModelNodeRequest.getMlInPlaceUpdateModelNodesRequest().concreteNodes().length);
    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        String modelId = "testModelId";
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        MLInPlaceUpdateModelNodeRequest inPlaceUpdateModelNodeRequest = new  MLInPlaceUpdateModelNodeRequest(
                new MLInPlaceUpdateModelNodesRequest(nodeIds, modelId, true)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        inPlaceUpdateModelNodeRequest.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLInPlaceUpdateModelNodeRequest parsedNodeRequest = new MLInPlaceUpdateModelNodeRequest(streamInput);

        assertEquals(inPlaceUpdateModelNodeRequest.getMlInPlaceUpdateModelNodesRequest().getModelId(), parsedNodeRequest.getMlInPlaceUpdateModelNodesRequest().getModelId());
    }
}
