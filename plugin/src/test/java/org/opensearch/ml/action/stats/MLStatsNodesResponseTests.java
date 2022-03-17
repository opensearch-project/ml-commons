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
import org.opensearch.ml.stats.StatNames;
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
        DiscoveryNode node = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<String, Object> statsToValues = new HashMap<>();
        statsToValues.put(StatNames.ML_TOTAL_REQUEST_COUNT, 100);
        nodes.add(new MLStatsNodeResponse(node, statsToValues));
        List<FailedNodeException> failures = new ArrayList<>();
        MLStatsNodesResponse response = new MLStatsNodesResponse(clusterName, nodes, failures);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"node0\":{\"ml_total_request_count\":100}}", taskContent);
    }
}
