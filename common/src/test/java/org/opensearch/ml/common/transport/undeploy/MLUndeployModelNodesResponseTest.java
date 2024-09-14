package org.opensearch.ml.common.transport.undeploy;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class MLUndeployModelNodesResponseTest {

    @Mock
    private ClusterName clusterName;
    private DiscoveryNode node1;
    private DiscoveryNode node2;
    private Map<String, Integer> modelWorkerNodeCounts;

    @Before
    public void setUp() throws Exception {
        clusterName = new ClusterName("clusterName");
        node1 = new DiscoveryNode(
            "foo1",
            "foo1",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        node2 = new DiscoveryNode(
            "foo2",
            "foo2",
            new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
            Collections.emptyMap(),
            Collections.singleton(CLUSTER_MANAGER_ROLE),
            Version.CURRENT
        );
        modelWorkerNodeCounts = new HashMap<>();
        modelWorkerNodeCounts.put("modelId1", 1);
    }

    @Test
    public void testSerializationDeserialization1() throws IOException {
        List<MLUndeployModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUndeployModelNodesResponse newResponse = new MLUndeployModelNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    @Test
    public void testToXContent() throws IOException {
        List<MLUndeployModelNodeResponse> nodes = new ArrayList<>();

        Map<String, String> modelToUndeployStatus1 = new HashMap<>();
        modelToUndeployStatus1.put("modelId1", "response");
        Map<String, String[]> modelWorkerNodeCounts1 = new HashMap<>();
        modelWorkerNodeCounts1.put("modelId1", new String[] { "mockNode1" });
        nodes.add(new MLUndeployModelNodeResponse(node1, modelToUndeployStatus1, modelWorkerNodeCounts1));

        Map<String, String> modelToUndeployStatus2 = new HashMap<>();
        modelToUndeployStatus2.put("modelId2", "response");
        Map<String, String[]> modelWorkerNodeCounts2 = new HashMap<>();
        modelWorkerNodeCounts2.put("modelId2", new String[] { "mockNode2" });
        nodes.add(new MLUndeployModelNodeResponse(node2, modelToUndeployStatus2, modelWorkerNodeCounts2));

        List<FailedNodeException> failures = new ArrayList<>();
        MLUndeployModelNodesResponse response = new MLUndeployModelNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{\"foo1\":{\"stats\":{\"modelId1\":\"response\"}},\"foo2\":{\"stats\":{\"modelId2\":\"response\"}}}", jsonStr);
    }
}
