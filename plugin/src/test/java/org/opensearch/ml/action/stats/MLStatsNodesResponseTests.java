/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.io.stream.BytesStreamOutput;
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
}
