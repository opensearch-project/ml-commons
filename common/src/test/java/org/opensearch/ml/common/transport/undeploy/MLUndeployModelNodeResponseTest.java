package org.opensearch.ml.common.transport.undeploy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.transport.TransportAddress;

@RunWith(MockitoJUnitRunner.class)
public class MLUndeployModelNodeResponseTest {

    @Mock
    private DiscoveryNode localNode;

    private Map<String, String[]> modelWorkerNodeCounts;

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
        modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", new String[] { "node" });
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelId1", "response");
        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, modelToDeployStatus, modelWorkerNodeCounts);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUndeployModelNodeResponse newResponse = new MLUndeployModelNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testSerializationDeserialization_NullModelDeployStatus() throws IOException {
        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, null, null);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUndeployModelNodeResponse newResponse = new MLUndeployModelNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testReadProfile() throws IOException {
        MLUndeployModelNodeResponse response = new MLUndeployModelNodeResponse(localNode, new HashMap<>(), new HashMap<>());
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUndeployModelNodeResponse newResponse = MLUndeployModelNodeResponse.readStats(output.bytes().streamInput());
        assertNotEquals(newResponse, response);
    }
}
