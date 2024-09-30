/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.update_cache;

import static org.junit.Assert.assertEquals;
import static org.opensearch.cluster.node.DiscoveryNodeRole.CLUSTER_MANAGER_ROLE;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
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
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.transport.TransportAddress;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

@RunWith(MockitoJUnitRunner.class)
public class MLUpdateModelCacheNodesResponseTest {

    @Mock
    private ClusterName clusterName;
    private DiscoveryNode node1;
    private DiscoveryNode node2;

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
    }

    @Test
    public void testSerializationDeserialization1() throws IOException {
        List<MLUpdateModelCacheNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLUpdateModelCacheNodesResponse response = new MLUpdateModelCacheNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLUpdateModelCacheNodesResponse newResponse = new MLUpdateModelCacheNodesResponse(output.bytes().streamInput());
        assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    @Test
    public void testToXContent() throws IOException {
        List<MLUpdateModelCacheNodeResponse> nodes = new ArrayList<>();

        Map<String, String> updateModelCacheStatus1 = Map.of("modelId1", "response");
        nodes.add(new MLUpdateModelCacheNodeResponse(node1, updateModelCacheStatus1));

        Map<String, String> updateModelCacheStatus2 = Map.of("modelId2", "response");
        nodes.add(new MLUpdateModelCacheNodeResponse(node2, updateModelCacheStatus2));

        List<FailedNodeException> failures = new ArrayList<>();
        MLUpdateModelCacheNodesResponse response = new MLUpdateModelCacheNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{\"foo1\":{\"stats\":{\"modelId1\":\"response\"}},\"foo2\":{\"stats\":{\"modelId2\":\"response\"}}}", jsonStr);
    }

    @Test
    public void testNullUpdateModelCacheStatusToXContent() throws IOException {
        List<MLUpdateModelCacheNodeResponse> nodes = new ArrayList<>();
        nodes.add(new MLUpdateModelCacheNodeResponse(node1, null));
        List<FailedNodeException> failures = new ArrayList<>();
        MLUpdateModelCacheNodesResponse response = new MLUpdateModelCacheNodesResponse(clusterName, nodes, failures);
        XContentBuilder builder = XContentFactory.jsonBuilder();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String jsonStr = builder.toString();
        assertEquals("{}", jsonStr);
    }
}
