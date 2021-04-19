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
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.test.OpenSearchTestCase.buildNewFakeTransportAddress;

public class MLStatsNodeResponseTests {
    @Test
    public void testSerializationDeserialization() throws IOException {
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<String, Object> statsToValues = new HashMap<>();
        statsToValues.put("stat1", "value1");
        MLStatsNodeResponse response = new MLStatsNodeResponse(localNode, statsToValues);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodeResponse newResponse = new MLStatsNodeResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getStatsMap().size(), response.getStatsMap().size());
        for (String stat : newResponse.getStatsMap().keySet()) {
            Assert.assertEquals(response.getStatsMap().get(stat), newResponse.getStatsMap().get(stat));
        }
    }
}
