package org.opensearch.ml.common.transport.sync;

import static org.junit.Assert.*;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.transport.TransportAddress;

@RunWith(MockitoJUnitRunner.class)
public class MLSyncUpNodeResponseTest {

    private DiscoveryNode localNode;
    private final String modelStatus = "modelStatus";
    private final String[] loadedModelIds = { "loadedModelIds" };
    private final String[] runningLoadModelTaskIds = { "runningLoadModelTaskIds" };
    private final String[] runningLoadModelIds = { "modelid1" };

    @Before
    public void setUp() throws Exception {
        localNode = new DiscoveryNode(
            "foo0",
            "foo0",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        MLSyncUpNodeResponse response = new MLSyncUpNodeResponse(
            localNode,
            modelStatus,
            loadedModelIds,
            runningLoadModelIds,
            runningLoadModelTaskIds
        );
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLSyncUpNodeResponse newResponse = new MLSyncUpNodeResponse(output.bytes().streamInput());
        assertNotEquals(newResponse, response);
        assertEquals(newResponse.getNode().getName(), response.getNode().getName());
        assertEquals(newResponse.getModelStatus(), response.getModelStatus());
        assertArrayEquals(newResponse.getDeployedModelIds(), response.getDeployedModelIds());
        assertArrayEquals(newResponse.getRunningDeployModelTaskIds(), response.getRunningDeployModelTaskIds());
    }

    @Test
    public void testReadProfile() throws IOException {
        MLSyncUpNodeResponse response = new MLSyncUpNodeResponse(
            localNode,
            modelStatus,
            loadedModelIds,
            runningLoadModelIds,
            runningLoadModelTaskIds
        );
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLSyncUpNodeResponse newResponse = MLSyncUpNodeResponse.readStats(output.bytes().streamInput());
        assertNotEquals(newResponse, response);
        assertEquals(newResponse.getNode().getName(), response.getNode().getName());
        assertEquals(newResponse.getModelStatus(), response.getModelStatus());
        assertArrayEquals(newResponse.getDeployedModelIds(), response.getDeployedModelIds());
        assertArrayEquals(newResponse.getRunningDeployModelTaskIds(), response.getRunningDeployModelTaskIds());

    }
}
