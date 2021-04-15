package org.opensearch.ml.action.stats;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.action.FailedNodeException;
import org.opensearch.cluster.ClusterName;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MLStatsNodesResponseTests {
    @Test
    public void testSerializationDeserialization() throws IOException {
        ClusterName clusterName = new ClusterName("clusterName");
        List<MLStatsNodeResponse> responseList = new ArrayList<>();
        List<FailedNodeException> failuresList = new ArrayList<>();
        MLStatsNodesResponse response = new MLStatsNodesResponse(clusterName, responseList, failuresList);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodesResponse newResponse = new MLStatsNodesResponse(output.bytes().streamInput());
        Assert.assertEquals(
                newResponse.getNodes().size(),
                response.getNodes().size()
        );
    }
}
