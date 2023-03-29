/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.stats;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.stats.ActionName;
import org.opensearch.ml.stats.MLActionLevelStat;
import org.opensearch.ml.stats.MLActionStats;
import org.opensearch.ml.stats.MLAlgoStats;
import org.opensearch.ml.stats.MLNodeLevelStat;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class MLStatsNodeResponseTests extends OpenSearchTestCase {
    private MLStatsNodeResponse response;
    private DiscoveryNode node;
    private long totalRequestCount = 100l;

    @Before
    public void setup() {
        node = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<MLNodeLevelStat, Object> statsToValues = new HashMap<>();
        statsToValues.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, 100);
        response = new MLStatsNodeResponse(node, statsToValues);
    }

    public void testSerializationDeserialization() throws IOException {
        DiscoveryNode localNode = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<MLNodeLevelStat, Object> statsToValues = new HashMap<>();
        statsToValues.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, 10l);
        MLStatsNodeResponse response = new MLStatsNodeResponse(localNode, statsToValues);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodeResponse newResponse = new MLStatsNodeResponse(output.bytes().streamInput());
        Assert.assertEquals(newResponse.getNodeLevelStatSize(), response.getNodeLevelStatSize());
    }

    public void testToXContent_NodeLevelStats() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"ml_node_total_request_count\":100}", taskContent);
    }

    public void testToXContent_AlgorithmStats() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(null);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String taskContent = TestHelper.xContentBuilderToString(builder);
        assertEquals("{\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":100}}}}", taskContent);
    }

    public void testWriteTo_AlgoStats() throws IOException {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(null);
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodeResponse newResponse = new MLStatsNodeResponse(output.bytes().streamInput());
        assertEquals(0, newResponse.getNodeLevelStatSize());
        assertEquals(1, newResponse.getAlgorithmStatSize());
        assertTrue(newResponse.hasAlgorithmStats(FunctionName.KMEANS));
        MLActionStats stats = newResponse.getAlgorithmStats(FunctionName.KMEANS).getActionStats(ActionName.TRAIN);
        assertEquals(totalRequestCount, stats.getActionStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));
    }

    private MLStatsNodeResponse createResponseWithDefaultAlgoStats(Map<MLNodeLevelStat, Object> nodeStats) {
        Map<FunctionName, MLAlgoStats> algoStats = new HashMap<>();
        Map<MLActionLevelStat, Object> actionStats = ImmutableMap.of(MLActionLevelStat.ML_ACTION_REQUEST_COUNT, totalRequestCount);
        Map<ActionName, MLActionStats> stats = ImmutableMap.of(ActionName.TRAIN, new MLActionStats(actionStats));
        algoStats.put(FunctionName.KMEANS, new MLAlgoStats(stats));

        MLStatsNodeResponse response = new MLStatsNodeResponse(node, nodeStats, algoStats);
        return response;
    }

    public void testToXContent_WithAlgoStats() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        DiscoveryNode node = new DiscoveryNode("node0", buildNewFakeTransportAddress(), Version.CURRENT);
        Map<MLNodeLevelStat, Object> statsToValues = new HashMap<>();
        statsToValues.put(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, 100);
        Map<FunctionName, MLAlgoStats> algoStats = new HashMap<>();
        Map<ActionName, MLActionStats> algoActionStats = new HashMap<>();
        Map<MLActionLevelStat, Object> algoActionStatMap = new HashMap<>();
        algoActionStatMap.put(MLActionLevelStat.ML_ACTION_REQUEST_COUNT, 111);
        algoActionStatMap.put(MLActionLevelStat.ML_ACTION_FAILURE_COUNT, 22);
        algoActionStats.put(ActionName.TRAIN, new MLActionStats(algoActionStatMap));
        algoStats.put(FunctionName.KMEANS, new MLAlgoStats(algoActionStats));
        response = new MLStatsNodeResponse(node, statsToValues, algoStats);
        response.toXContent(builder, ToXContent.EMPTY_PARAMS);
        builder.endObject();
        String taskContent = TestHelper.xContentBuilderToString(builder);
        Set<String> validResult = ImmutableSet
            .of(
                "{\"ml_node_total_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_failure_count\":22,\"ml_action_request_count\":111}}}}",
                "{\"ml_node_total_request_count\":100,\"algorithms\":{\"kmeans\":{\"train\":{\"ml_action_request_count\":111,\"ml_action_failure_count\":22}}}}"
            );
        assertTrue(validResult.contains(taskContent));
    }

    public void testReadStats() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        response.writeTo(output);
        MLStatsNodeResponse mlStatsNodeResponse = MLStatsNodeResponse.readStats(output.bytes().streamInput());
        Integer expectedValue = (Integer) response.getNodeLevelStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT);
        assertEquals(expectedValue, mlStatsNodeResponse.getNodeLevelStat(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT));
    }

    public void testIsEmpty_NullNodeStats() {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(null);
        assertFalse(response.isEmpty());
    }

    public void testIsEmpty_EmptyNodeStats() {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(ImmutableMap.of());
        assertFalse(response.isEmpty());
    }

    public void testIsEmpty_NullAlgoStats() {
        assertFalse(response.isEmpty());
    }

    public void testIsEmpty_EmptyAlgoStats() {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(ImmutableMap.of());
        response.removeAlgorithmStats(FunctionName.KMEANS);
        assertTrue(response.isEmpty());
    }

    public void testIsEmpty_NonEmptyNodeAndAlgoStats() {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(
            ImmutableMap.of(MLNodeLevelStat.ML_NODE_TOTAL_REQUEST_COUNT, totalRequestCount)
        );
        assertFalse(response.isEmpty());
    }

    public void testGetNodeLevelStat_NonExistingStat() {
        assertNull(response.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT));
        assertEquals(1, response.getNodeLevelStatSize());
    }

    public void testGetNodeLevelStat_NullOrEmptyNodeStats() {
        MLStatsNodeResponse response = new MLStatsNodeResponse(node, null);
        assertNull(response.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT));
        assertEquals(0, response.getNodeLevelStatSize());

        response = new MLStatsNodeResponse(node, ImmutableMap.of());
        assertNull(response.getNodeLevelStat(MLNodeLevelStat.ML_NODE_EXECUTING_TASK_COUNT));
        assertEquals(0, response.getNodeLevelStatSize());
    }

    public void testGetAlgorithmLevelStat_NullAlgoStats() {
        assertNull(response.getAlgorithmStats(FunctionName.BATCH_RCF));
        assertEquals(0, response.getAlgorithmStatSize());
    }

    public void testGetAlgorithmLevelStat_EmptyAlgoStats() {
        MLStatsNodeResponse response = new MLStatsNodeResponse(node, null, ImmutableMap.of());
        assertNull(response.getAlgorithmStats(FunctionName.BATCH_RCF));
        assertEquals(0, response.getNodeLevelStatSize());
    }

    public void testGetAlgorithmLevelStat_NonExistingAlgo() {
        MLStatsNodeResponse response = createResponseWithDefaultAlgoStats(null);
        assertEquals(0, response.getNodeLevelStatSize());
        assertEquals(1, response.getAlgorithmStatSize());
        assertNotNull(response.getAlgorithmStats(FunctionName.KMEANS));
        response.removeAlgorithmStats(FunctionName.KMEANS);
        assertEquals(0, response.getAlgorithmStatSize());
        assertNull(response.getAlgorithmStats(FunctionName.KMEANS));
    }
}
