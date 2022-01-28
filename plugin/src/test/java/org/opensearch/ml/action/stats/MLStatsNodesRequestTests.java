/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;

public class MLStatsNodesRequestTests {
    @Test
    public void testSerializationDeserialization() throws IOException {
        MLStatsNodesRequest request = new MLStatsNodesRequest(("nodeId"));
        request.clear();

        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList("stat1"));

        for (String stat : statsToBeRetrieved) {
            request.addStat(stat);
        }
        BytesStreamOutput output = new BytesStreamOutput();
        request.writeTo(output);
        MLStatsNodesRequest newRequest = new MLStatsNodesRequest(output.bytes().streamInput());
        Assert.assertEquals(newRequest.getStatsToBeRetrieved().size(), request.getStatsToBeRetrieved().size());
        for (String stat : newRequest.getStatsToBeRetrieved()) {
            Assert.assertTrue(request.getStatsToBeRetrieved().contains(stat));
        }
    }
}
