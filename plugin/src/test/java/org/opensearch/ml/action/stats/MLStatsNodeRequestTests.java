/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.action.stats;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MLStatsNodeRequestTests {
    @Test
    public void testSerializationDeserialization() throws IOException {
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(("testNodeId"));
        mlStatsNodesRequest.clear();

        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList("testStat"));

        for (String stat : statsToBeRetrieved) {
            mlStatsNodesRequest.addStat(stat);
        }
        mlStatsNodesRequest.addAll(statsToBeRetrieved);
        BytesStreamOutput output = new BytesStreamOutput();
        MLStatsNodeRequest request = new MLStatsNodeRequest(mlStatsNodesRequest);
        request.writeTo(output);
        MLStatsNodeRequest newRequest = new MLStatsNodeRequest(output.bytes().streamInput());
        Assert.assertEquals(
                newRequest.getMlStatsNodesRequest().getStatsToBeRetrieved().size(),
                request.getMlStatsNodesRequest().getStatsToBeRetrieved().size()
        );
        for (String stat : newRequest.getMlStatsNodesRequest().getStatsToBeRetrieved()) {
            Assert.assertTrue(request.getMlStatsNodesRequest().getStatsToBeRetrieved().contains(stat));
        }
    }
}
