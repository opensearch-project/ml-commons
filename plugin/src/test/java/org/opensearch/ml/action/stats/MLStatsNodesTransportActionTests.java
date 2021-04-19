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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.ml.stats.InternalStatNames;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.stats.suppliers.SettableSupplier;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.monitor.jvm.JvmService;
import org.opensearch.monitor.jvm.JvmStats;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.transport.TransportService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MLStatsNodesTransportActionTests extends OpenSearchIntegTestCase {
    private MLStatsNodesTransportAction action;
    private MLStats mlStats;
    private Map<String, MLStat<?>> statsMap;
    private String clusterStatName1;
    private String nodeStatName1;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        clusterStatName1 = "clusterStat1";
        nodeStatName1 = "nodeStat1";

        statsMap = new HashMap<String, MLStat<?>>() {
            {
                put(nodeStatName1, new MLStat<>(false, new CounterSupplier()));
                put(clusterStatName1, new MLStat<>(true, new CounterSupplier()));
                put(InternalStatNames.JVM_HEAP_USAGE.getName(), new MLStat<>(true, new SettableSupplier()));
            }
        };

        mlStats = new MLStats(statsMap);
        JvmService jvmService = mock(JvmService.class);
        JvmStats jvmStats = mock(JvmStats.class);
        JvmStats.Mem mem = mock(JvmStats.Mem.class);

        when(jvmService.stats()).thenReturn(jvmStats);
        when(jvmStats.getMem()).thenReturn(mem);
        when(mem.getHeapUsedPercent()).thenReturn(randomShort());

        action = new MLStatsNodesTransportAction(
                client().threadPool(),
                clusterService(),
                mock(TransportService.class),
                mock(ActionFilters.class),
                mlStats,
                jvmService
        );
    }

    @Test
    public void testNewNodeRequest() {
        String nodeId = "nodeId1";
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(nodeId);

        MLStatsNodeRequest mlStatsNodeRequest1 = new MLStatsNodeRequest(mlStatsNodesRequest);
        MLStatsNodeRequest mlStatsNodeRequest2 = action.newNodeRequest(mlStatsNodesRequest);

        assertEquals(mlStatsNodeRequest1.getMlStatsNodesRequest(), mlStatsNodeRequest2.getMlStatsNodesRequest());
    }

    @Test
    public void testNewNodeResponse() throws IOException {
        Map<String, Object> statValues = new HashMap<>();
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        MLStatsNodeResponse statsNodeResponse = new MLStatsNodeResponse(localNode, statValues);
        BytesStreamOutput out = new BytesStreamOutput();
        statsNodeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLStatsNodeResponse newStatsNodeResponse = action.newNodeResponse(in);
        Assert.assertEquals(statsNodeResponse.getStatsMap().size(), newStatsNodeResponse.getStatsMap().size());
        for (String statName : newStatsNodeResponse.getStatsMap().keySet()) {
            Assert.assertTrue(statsNodeResponse.getStatsMap().containsKey(statName));
        }
    }

    @Test
    public void testNodeOperation() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest((nodeId));
        mlStatsNodesRequest.clear();

        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList(nodeStatName1));

        for (String stat : statsToBeRetrieved) {
            mlStatsNodesRequest.addStat(stat);
        }

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Map<String, Object> stats = response.getStatsMap();

        Assert.assertEquals(statsToBeRetrieved.size(), stats.size());
        for (String statName : stats.keySet()) {
            Assert.assertTrue(statsToBeRetrieved.contains(statName));
        }
    }

    @Test
    public void testNodeOperationWithJvmHeapUsage() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest((nodeId));
        mlStatsNodesRequest.clear();

        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList(nodeStatName1, InternalStatNames.JVM_HEAP_USAGE.getName()));

        for (String stat : statsToBeRetrieved) {
            mlStatsNodesRequest.addStat(stat);
        }

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Map<String, Object> stats = response.getStatsMap();

        Assert.assertEquals(statsToBeRetrieved.size(), stats.size());
        for (String statName : stats.keySet()) {
            Assert.assertTrue(statsToBeRetrieved.contains(statName));
        }
    }

    @Test
    public void testNodeOperationNotSupportedStat() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest((nodeId));
        mlStatsNodesRequest.clear();

        Set<String> statsToBeRetrieved = new HashSet<>(Arrays.asList("notSupportedStat"));

        for (String stat : statsToBeRetrieved) {
            mlStatsNodesRequest.addStat(stat);
        }

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Map<String, Object> stats = response.getStatsMap();

        Assert.assertEquals(0, stats.size());
    }

}
