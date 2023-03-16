package org.opensearch.ml.common.transport.unload;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.Strings;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.transport.TransportAddress;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class UnloadModelNodesResponseTest {

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
        List<UnloadModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        UnloadModelNodesResponse response = new UnloadModelNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        UnloadModelNodesResponse newResponse = new UnloadModelNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    @Test
    public void testToXContent() throws IOException {
        List<UnloadModelNodeResponse> nodes = new ArrayList<>();

        Map<String, String> modelToUnloadStatus1 = new HashMap<>();
        modelToUnloadStatus1.put("modelId1", "response");
        Map<String, Integer> modelWorkerNodeCounts1 = new HashMap<>();
        modelWorkerNodeCounts1.put("modelId1", 1);
        nodes.add(new UnloadModelNodeResponse(node1, modelToUnloadStatus1, modelWorkerNodeCounts1));

        Map<String, String> modelToUnloadStatus2 = new HashMap<>();
        modelToUnloadStatus2.put("modelId2", "response");
        Map<String, Integer> modelWorkerNodeCounts2 = new HashMap<>();
        modelWorkerNodeCounts2.put("modelId2", 2);
        nodes.add(new UnloadModelNodeResponse(node2, modelToUnloadStatus2, modelWorkerNodeCounts2));

        List<FailedNodeException> failures = new ArrayList<>();
        UnloadModelNodesResponse response = new UnloadModelNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = Strings.toString(builder);
        assertEquals(
                "{\"foo1\":{\"stats\":{\"modelId1\":\"response\"}},\"foo2\":{\"stats\":{\"modelId2\":\"response\"}}}",
                jsonStr
        );
    }
}
