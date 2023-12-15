/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update;

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
public class MLUpdateModelCacheNodesRequestTest {

    @Test
    public void testConstructorSerialization1() throws IOException {
        String modelId = "testModelId";
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        MLUpdateModelCacheNodeRequest updateModelCacheNodeRequest = new MLUpdateModelCacheNodeRequest(
                new MLUpdateModelCacheNodesRequest(nodeIds, modelId, true)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        updateModelCacheNodeRequest.writeTo(output);
        assertEquals("testModelId", updateModelCacheNodeRequest.getUpdateModelCacheNodesRequest().getModelId());
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
        MLUpdateModelCacheNodeRequest updateModelCacheNodeRequest = new MLUpdateModelCacheNodeRequest(
                new MLUpdateModelCacheNodesRequest(nodes, modelId, true)
        );
        assertEquals(2, updateModelCacheNodeRequest.getUpdateModelCacheNodesRequest().concreteNodes().length);
    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        String modelId = "testModelId";
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        MLUpdateModelCacheNodeRequest updateModelCacheNodeRequest = new MLUpdateModelCacheNodeRequest(
                new MLUpdateModelCacheNodesRequest(nodeIds, modelId, true)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        updateModelCacheNodeRequest.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUpdateModelCacheNodeRequest parsedNodeRequest = new MLUpdateModelCacheNodeRequest(streamInput);

        assertEquals(updateModelCacheNodeRequest.getUpdateModelCacheNodesRequest().getModelId(), parsedNodeRequest.getUpdateModelCacheNodesRequest().getModelId());
    }
}
