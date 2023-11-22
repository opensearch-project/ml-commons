package org.opensearch.ml.common.transport.sync;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.transport.TransportAddress;

@RunWith(MockitoJUnitRunner.class)
public class MLSyncUpNodeRequestTest {

    private DiscoveryNode localNode1;
    private DiscoveryNode localNode2;
    private DiscoveryNode localNode3;

    @Mock
    private MLSyncUpInput syncUpInput;

    @Before
    public void setUp() throws Exception {
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
        localNode3 = new DiscoveryNode(
            "foo3",
            "foo3",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );

        Map<String, String[]> addedWorkerNodes = new HashMap<>();
        Map<String, String[]> removedWorkerNodes = new HashMap<>();
        Map<String, Set<String>> modelRoutingTable = new HashMap<>();
        Map<String, Set<String>> runningDeployModelTasks = new HashMap<>();

        syncUpInput = MLSyncUpInput
            .builder()
            .getDeployedModels(true)
            .addedWorkerNodes(addedWorkerNodes)
            .removedWorkerNodes(removedWorkerNodes)
            .modelRoutingTable(modelRoutingTable)
            .runningDeployModelTasks(runningDeployModelTasks)
            .clearRoutingTable(true)
            .syncRunningDeployModelTasks(true)
            .build();
    }

    @Test
    public void testConstructorSerialization1() throws IOException {
        String[] nodeIds = { "id1", "id2", "id3" };
        MLSyncUpNodeRequest syncUpNodeRequest = new MLSyncUpNodeRequest(new MLSyncUpNodesRequest(nodeIds, syncUpInput));
        BytesStreamOutput output = new BytesStreamOutput();

        syncUpNodeRequest.writeTo(output);

        assertEquals(3, syncUpNodeRequest.getSyncUpNodesRequest().nodesIds().length);
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isSyncRunningDeployModelTasks());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());

    }

    @Test
    public void testConstructorSerialization2() {
        DiscoveryNode[] nodeIds = { localNode1, localNode2, localNode3 };
        MLSyncUpNodeRequest syncUpNodeRequest = new MLSyncUpNodeRequest(new MLSyncUpNodesRequest(nodeIds, syncUpInput));

        assertEquals(3, syncUpNodeRequest.getSyncUpNodesRequest().concreteNodes().length);
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isSyncRunningDeployModelTasks());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());
    }

    @Test
    public void testConstructorSerialization3() {
        MLSyncUpNodeRequest syncUpNodeRequest = new MLSyncUpNodeRequest(new MLSyncUpNodesRequest(localNode1, localNode2, localNode3));
        syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().setClearRoutingTable(true);
        syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().setSyncRunningDeployModelTasks(true);
        syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().setClearRoutingTable(true);

        assertEquals(3, syncUpNodeRequest.getSyncUpNodesRequest().concreteNodes().length);
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isSyncRunningDeployModelTasks());
        assertTrue(syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable());
    }

    @Test
    public void testConstructorFromInputStream() throws IOException {
        String[] nodeIds = { "id1", "id2", "id3" };
        MLSyncUpNodeRequest syncUpNodeRequest = new MLSyncUpNodeRequest(new MLSyncUpNodesRequest(nodeIds, syncUpInput));
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        syncUpNodeRequest.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLSyncUpNodeRequest parsedNodeRequest = new MLSyncUpNodeRequest(streamInput);

        assertEquals(3, parsedNodeRequest.getSyncUpNodesRequest().nodesIds().length);
        assertEquals(
            parsedNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable(),
            syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable()
        );
        assertEquals(
            parsedNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable(),
            syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isSyncRunningDeployModelTasks()
        );
        assertEquals(
            parsedNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable(),
            syncUpNodeRequest.getSyncUpNodesRequest().getSyncUpInput().isClearRoutingTable()
        );
    }

}
