package org.opensearch.ml.common.transport.deploy;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

@RunWith(MockitoJUnitRunner.class)
public class MLDeployModelNodesResponseTest {

    @Mock
    private ClusterName clusterName;

    @Before
    public void setUp() throws Exception {
        clusterName = new ClusterName("clusterName");
    }

    @Test
    public void testSerializationDeserialization() throws IOException {
        List<MLDeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLDeployModelNodesResponse response = new MLDeployModelNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLDeployModelNodesResponse newResponse = new MLDeployModelNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    @Test
    public void testToXContent() throws IOException {
        List<MLDeployModelNodeResponse> nodes = new ArrayList<>();
        DiscoveryNode node1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        Map<String, String> modelToDeployStatus1 = new HashMap<>();
        modelToDeployStatus1.put("modelName:version1", "response");
        nodes.add(new MLDeployModelNodeResponse(node1, modelToDeployStatus1));

        DiscoveryNode node2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        Map<String, String> modelToDeployStatus2 = new HashMap<>();
        modelToDeployStatus2.put("modelName:version2", "response");
        nodes.add(new MLDeployModelNodeResponse(node2, modelToDeployStatus2));

        List<FailedNodeException> failures = new ArrayList<>();
        MLDeployModelNodesResponse response = new MLDeployModelNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals(
            "{\"foo1\":{\"stats\":{\"modelName:version1\":\"response\"}},\"foo2\":{\"stats\":{\"modelName:version2\":\"response\"}}}",
            jsonStr
        );
    }
}
