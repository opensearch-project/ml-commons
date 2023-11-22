package org.opensearch.ml.common.transport.deploy;

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
public class MLDeployModelNodeResponseTest {

    @Mock
    private DiscoveryNode localNode;

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
        Map<String, String> modelToDeployStatus = new HashMap<>();
        modelToDeployStatus.put("modelName:version", "response");
        MLDeployModelNodeResponse response = new MLDeployModelNodeResponse(localNode, modelToDeployStatus);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLDeployModelNodeResponse newResponse = new MLDeployModelNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testSerializationDeserialization_NullModelDeployStatus() throws IOException {
        MLDeployModelNodeResponse response = new MLDeployModelNodeResponse(localNode, null);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLDeployModelNodeResponse newResponse = new MLDeployModelNodeResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNode().getId(), response.getNode().getId());
    }

    @Test
    public void testReadProfile() throws IOException {
        MLDeployModelNodeResponse response = new MLDeployModelNodeResponse(localNode, new HashMap<>());
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLDeployModelNodeResponse newResponse = MLDeployModelNodeResponse.readStats(output.bytes().streamInput());
        assertNotEquals(newResponse, response);
    }
}
