/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Set;

import org.junit.Assert;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatsNodesRequestTests extends OpenSearchTestCase {

    public void testSerializationDeserialization() throws IOException {
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { "testNodeId" }, new MLStatsInput());

        mlStatsNodesRequest.addNodeLevelStats(Set.of(MLNodeLevelStat.ML_EXECUTING_TASK_COUNT));
        BytesStreamOutput output = new BytesStreamOutput();
        MLStatsNodeRequest request = new MLStatsNodeRequest(mlStatsNodesRequest);
        request.writeTo(output);
        MLStatsNodeRequest newRequest = new MLStatsNodeRequest(output.bytes().streamInput());
        Assert
            .assertEquals(
                newRequest.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().size(),
                request.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().size()
            );
        for (Enum stat : newRequest.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats()) {
            Assert.assertTrue(request.getMlStatsNodesRequest().getMlStatsInput().getNodeLevelStats().contains(stat));
        }
    }
}
