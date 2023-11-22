package org.opensearch.ml.common.transport.undeploy;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.transport.TransportAddress;

@RunWith(MockitoJUnitRunner.class)
public class MLUndeployModelNodesRequestTest {

    private DiscoveryNode localNode1;
    private DiscoveryNode localNode2;

    @Test
    public void testConstructorSerialization1() throws IOException {

        String[] modelIds = { "modelId1", "modelId2", "modelId3" };
        String[] nodeIds = { "nodeId1", "nodeId2", "nodeId3" };

        MLUndeployModelNodeRequest undeployModelNodeRequest = new MLUndeployModelNodeRequest(
            new MLUndeployModelNodesRequest(nodeIds, modelIds)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        undeployModelNodeRequest.writeTo(output);
        assertArrayEquals(
            new String[] { "modelId1", "modelId2", "modelId3" },
            undeployModelNodeRequest.getMlUndeployModelNodesRequest().getModelIds()
        );

    }

    @Test
    public void testConstructorSerialization2() throws IOException {

        localNode1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        localNode2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        MLUndeployModelNodeRequest undeployModelNodeRequest = new MLUndeployModelNodeRequest(
            new MLUndeployModelNodesRequest(localNode1, localNode2)
        );
        assertEquals(2, undeployModelNodeRequest.getMlUndeployModelNodesRequest().concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {

        String[] modelIds = { "modelId1", "modelId2", "modelId3" };
        String[] nodeIds = { "nodeId1", "nodeId2", "nodeId3" };

        MLUndeployModelNodeRequest undeployModelNodeRequest = new MLUndeployModelNodeRequest(
            new MLUndeployModelNodesRequest(nodeIds, modelIds)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        undeployModelNodeRequest.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLUndeployModelNodeRequest parsedNodeRequest = new MLUndeployModelNodeRequest(streamInput);

        assertArrayEquals(
            undeployModelNodeRequest.getMlUndeployModelNodesRequest().getModelIds(),
            parsedNodeRequest.getMlUndeployModelNodesRequest().getModelIds()
        );

    }
}
