package org.opensearch.ml.common.transport.load;

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
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

@RunWith(MockitoJUnitRunner.class)
public class LoadModelNodesResponseTest {

    @Mock
    private ClusterName clusterName;

    @Before
    public void setUp() throws Exception {
        clusterName = new ClusterName("clusterName");
    }


    @Test
    public void testSerializationDeserialization() throws IOException {
        List<LoadModelNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        LoadModelNodesResponse response = new LoadModelNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        LoadModelNodesResponse newResponse = new LoadModelNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    @Test
    public void testToXContent() throws IOException {
        List<LoadModelNodeResponse> nodes = new ArrayList<>();
        DiscoveryNode node1 = new DiscoveryNode(
                "foo1",
                "foo1",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        Map<String, String> modelToLoadStatus1 = new HashMap<>();
        modelToLoadStatus1.put("modelName:version1", "response");
        nodes.add(new LoadModelNodeResponse(node1, modelToLoadStatus1));

        DiscoveryNode node2 = new DiscoveryNode(
                "foo2",
                "foo2",
                new TransportAddress(InetAddress.getLoopbackAddress(), 9300),
                Collections.emptyMap(),
                Collections.singleton(CLUSTER_MANAGER_ROLE),
                Version.CURRENT
        );
        Map<String, String> modelToLoadStatus2 = new HashMap<>();
        modelToLoadStatus2.put("modelName:version2", "response");
        nodes.add(new LoadModelNodeResponse(node2, modelToLoadStatus2));

        List<FailedNodeException> failures = new ArrayList<>();
        LoadModelNodesResponse response = new LoadModelNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = Strings.toString(builder);
        assertEquals(
                "{\"foo1\":{\"stats\":{\"modelName:version1\":\"response\"}},\"foo2\":{\"stats\":{\"modelName:version2\":\"response\"}}}",
                jsonStr
        );
    }
}
