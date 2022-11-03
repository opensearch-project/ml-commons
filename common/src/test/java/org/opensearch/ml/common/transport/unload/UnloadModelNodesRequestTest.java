package org.opensearch.ml.common.transport.unload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.transport.TransportAddress;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class UnloadModelNodesRequestTest {

    private DiscoveryNode localNode1;
    private DiscoveryNode localNode2;

    @Test
    public void testConstructorSerialization1() throws IOException {

        String[] modelIds = {"modelId1", "modelId2", "modelId3"};
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        UnloadModelNodeRequest unloadModelNodeRequest = new UnloadModelNodeRequest(
                new UnloadModelNodesRequest(nodeIds, modelIds)
        );
        BytesStreamOutput output = new BytesStreamOutput();

        unloadModelNodeRequest.writeTo(output);
        assertArrayEquals(new String[] {"modelId1", "modelId2", "modelId3"}, unloadModelNodeRequest.getUnloadModelNodesRequest().getModelIds());

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

        UnloadModelNodeRequest unloadModelNodeRequest = new UnloadModelNodeRequest(
                new UnloadModelNodesRequest(localNode1,localNode2)
        );
        assertEquals(2, unloadModelNodeRequest.getUnloadModelNodesRequest().concreteNodes().length);

    }

    @Test
    public void testConstructorFromInputStream() throws IOException {

        String[] modelIds = {"modelId1", "modelId2", "modelId3"};
        String[] nodeIds = {"nodeId1", "nodeId2", "nodeId3"};

        UnloadModelNodeRequest unloadModelNodeRequest = new UnloadModelNodeRequest(
                new UnloadModelNodesRequest(nodeIds, modelIds)
        );
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        unloadModelNodeRequest.writeTo(bytesStreamOutput);

        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        UnloadModelNodeRequest parsedNodeRequest = new UnloadModelNodeRequest(streamInput);

        assertArrayEquals(unloadModelNodeRequest.getUnloadModelNodesRequest().getModelIds(), parsedNodeRequest.getUnloadModelNodesRequest().getModelIds());

    }
}
