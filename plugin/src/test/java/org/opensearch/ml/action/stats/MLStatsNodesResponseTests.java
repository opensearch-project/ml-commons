/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.opensearch.Version;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.ToXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatsNodesResponseTests extends OpenSearchTestCase {

    public void testSerializationDeserialization() throws IOException {
        ClusterName clusterName = new ClusterName("clusterName");
        List<MLStatsNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLStatsNodesResponse response = new MLStatsNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodesResponse newResponse = new MLStatsNodesResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getNodes().size(), response.getNodes().size());
    }

    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        ClusterName clusterName = new ClusterName("test");
        List<MLStatsNodeResponse> nodes = new ArrayList<>();

        DiscoveryNode node1 = new DiscoveryNode("node1", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<MLNodeLevelStat, Object> nodeLevelStats1 = new HashMap<>();
        nodeLevelStats1.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, 100);
        nodes.add(new MLStatsNodeResponse(node1, nodeLevelStats1));

        DiscoveryNode node2 = new DiscoveryNode("node2", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<MLNodeLevelStat, Object> nodeLevelStats2 = new HashMap<>();
        nodeLevelStats2.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, 200);
        nodes.add(new MLStatsNodeResponse(node2, nodeLevelStats2));

        List<FailedNodeException> failures = new ArrayList<>();
        MLStatsNodesResponse response = new MLStatsNodesResponse(clusterName, nodes, failures);
        builder.startObject();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals(
            "{\"nodes\":{\"node1\":{\"ml_node_total_request_count\":100},\"node2\":{\"ml_node_total_request_count\":200}}}",
            taskContent
        );
    }
}
