/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opensearch.ml.stats.MLNodeLevelStat.ML_JVM_HEAP_USAGE;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.env.Environment;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLActionStats;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLClusterLevelStat;
import org.opensearch.ml.stats.MLModelStats;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.stats.MLStat;
import org.opensearch.ml.stats.MLStatLevel;
import org.opensearch.ml.stats.MLStats;
import org.opensearch.ml.stats.MLStatsInput;
import org.opensearch.ml.stats.suppliers.CounterSupplier;
import org.opensearch.ml.stats.suppliers.SettableSupplier;
import org.opensearch.test.OpenSearchIntegTestCase;
import org.opensearch.transport.TransportService;

public class MLStatsNodesTransportActionTests extends OpenSearchIntegTestCase {
    private MLStatsNodesTransportAction action;
    private MLStats mlStats;
    private Map<Enum, MLStat<?>> statsMap;
    private MLClusterLevelStat clusterStatName1;
    private MLNodeLevelStat nodeStatName1;
    private Environment environment;

    private final String modelId = "model_id";

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        clusterStatName1 = MLClusterLevelStat.ML_MODEL_COUNT;
        nodeStatName1 = MLNodeLevelStat.ML_EXECUTING_TASK_COUNT;

        statsMap = new HashMap<>() {
            {
                put(nodeStatName1, new MLStat<>(false, new CounterSupplier()));
                put(clusterStatName1, new MLStat<>(true, new CounterSupplier()));
                put(ML_JVM_HEAP_USAGE, new MLStat<>(true, new SettableSupplier()));
            }
        };

        mlStats = new MLStats(statsMap);
        environment = mock(Environment.class);
        Settings settings = Settings.builder().build();
        when(environment.settings()).thenReturn(settings);

        action = new MLStatsNodesTransportAction(
            client().threadPool(),
            clusterService(),
            mock(TransportService.class),
            mock(ActionFilters.class),
            mlStats,
            environment
        );
    }

    public void testNewNodeRequest() {
        String nodeId = "nodeId1";
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        MLStatsNodeRequest mlStatsNodeRequest1 = new MLStatsNodeRequest(mlStatsNodesRequest);
        MLStatsNodeRequest mlStatsNodeRequest2 = action.newNodeRequest(mlStatsNodesRequest);

        assertEquals(mlStatsNodeRequest1.getMlStatsNodesRequest(), mlStatsNodeRequest2.getMlStatsNodesRequest());
    }

    public void testNewNodeResponse() throws IOException {
        Map<MLNodeLevelStat, Object> statValues = new HashMap<>();
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        MLStatsNodeResponse statsNodeResponse = new MLStatsNodeResponse(localNode, statValues);
        BytesStreamOutput out = new BytesStreamOutput();
        statsNodeResponse.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        MLStatsNodeResponse newStatsNodeResponse = action.newNodeResponse(in);
        Assert.assertEquals(statsNodeResponse.getNodeLevelStatSize(), newStatsNodeResponse.getAlgorithmStatSize());
        Assert.assertEquals(statsNodeResponse.getNodeLevelStatSize(), newStatsNodeResponse.getModelStatSize());
    }

    public void testNodeOperation() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        Set<MLNodeLevelStat> statsToBeRetrieved = Set.of(nodeStatName1);
        mlStatsNodesRequest.addNodeLevelStats(statsToBeRetrieved);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Assert.assertEquals(1, response.getNodeLevelStatSize());
        assertNotNull(response.getNodeLevelStat(nodeStatName1));
    }

    public void testNodeOperationWithJvmHeapUsage() {
        String nodeId = clusterService().localNode().getId();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, new MLStatsInput());

        Set<MLNodeLevelStat> statsToBeRetrieved = Set.of(ML_JVM_HEAP_USAGE);

        mlStatsNodesRequest.addNodeLevelStats(statsToBeRetrieved);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        Assert.assertEquals(statsToBeRetrieved.size(), response.getNodeLevelStatSize());
        assertNotNull(response.getNodeLevelStat(ML_JVM_HEAP_USAGE));
    }

    public void testNodeOperation_NoNodeLevelStat() {
        String nodeId = clusterService().localNode().getId();
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM, MLStatLevel.MODEL)).build();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, mlStatsInput);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        assertEquals(0, response.getNodeLevelStatSize());
    }

    public void testNodeOperation_NoNodeLevelStat_AlgoStat() {
        MLStats mlStats = new MLStats(statsMap);
        mlStats.createCounterStatIfAbsent(FunctionName.KMEANS, ActionName.TRAIN, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();
        mlStats.createModelCounterStatIfAbsent(modelId, ActionName.PREDICT, MLActionLevelStat.ML_ACTION_REQUEST_COUNT).increment();

        MLStatsNodesTransportAction action = new MLStatsNodesTransportAction(
            client().threadPool(),
            clusterService(),
            mock(TransportService.class),
            mock(ActionFilters.class),
            mlStats,
            environment
        );

        String nodeId = clusterService().localNode().getId();
        MLStatsInput mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM, MLStatLevel.MODEL)).build();
        MLStatsNodesRequest mlStatsNodesRequest = new MLStatsNodesRequest(new String[] { nodeId }, mlStatsInput);

        MLStatsNodeResponse response = action.nodeOperation(new MLStatsNodeRequest(mlStatsNodesRequest));

        assertEquals(0, response.getNodeLevelStatSize());
        assertEquals(1, response.getAlgorithmStatSize());
        assertEquals(1, response.getModelStatSize());
        MLAlgoStats algorithmStats = response.getAlgorithmStats(FunctionName.KMEANS);
        assertNotNull(algorithmStats);
        MLActionStats actionStats = algorithmStats.getActionStats(ActionName.TRAIN);
        assertNotNull(actionStats);
        assertEquals(1l, actionStats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        MLModelStats modelStats = response.getModelStats(modelId);
        assertNotNull(modelStats);
        actionStats = modelStats.getActionStats(ActionName.PREDICT);
        assertNotNull(actionStats);
        assertEquals(1l, actionStats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));
    }

}
