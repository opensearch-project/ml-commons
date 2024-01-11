/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

public class MLStatsInputTests extends OpenSearchTestCase {

    private MLStatsInput mlStatsInput;
    private String node1 = "node1";
    private String node2 = "node2";

    private String modelId = "model_id";

    @Before
    public void setup() {
        mlStatsInput = MLStatsInput
            .builder()
            .targetStatLevels(EnumSet.allOf(MLStatLevel.class))
            .clusterLevelStats(EnumSet.allOf(MLClusterLevelStat.class))
            .nodeLevelStats(EnumSet.allOf(MLNodeLevelStat.class))
            .actionLevelStats(EnumSet.allOf(MLActionLevelStat.class))
            .nodeIds(Set.of(node1, node2))
            .algorithms(EnumSet.allOf(FunctionName.class))
            .models(Set.of(modelId))
            .actions(EnumSet.allOf(ActionName.class))
            .build();
    }

    public void testSerializationDeserialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlStatsInput.writeTo(output);
        MLStatsInput parsedMLStatsInput = new MLStatsInput(output.bytes().streamInput());
        verifyParsedMLStatsInput(parsedMLStatsInput);
    }

    public void testParseMLStatsInput() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        mlStatsInput.toXContent(builder, EMPTY_PARAMS);
        String content = TestHelper.xContentBuilderToString(builder);
        XContentParser parser = TestHelper.parser(content);
        MLStatsInput parsedMLStatsInput = MLStatsInput.parse(parser);
        verifyParsedMLStatsInput(parsedMLStatsInput);
    }

    public void testRetrieveAll() {
        assertFalse(mlStatsInput.retrieveStatsForAllAlgos());
        assertFalse(mlStatsInput.retrieveStatsForAllModels());
        assertFalse(mlStatsInput.retrieveAllNodeLevelStats());
        assertFalse(mlStatsInput.retrieveStatsForAllActions());
        assertFalse(mlStatsInput.retrieveAllClusterLevelStats());
        assertFalse(mlStatsInput.retrieveStatsOnAllNodes());
        assertFalse(mlStatsInput.retrieveAllActionLevelStats());

        MLStatsInput mlStatsInput = MLStatsInput.builder().build();
        assertTrue(mlStatsInput.retrieveStatsForAllModels());
        assertTrue(mlStatsInput.retrieveStatsForAllAlgos());
        assertTrue(mlStatsInput.retrieveAllNodeLevelStats());
        assertTrue(mlStatsInput.retrieveStatsForAllActions());
        assertTrue(mlStatsInput.retrieveAllClusterLevelStats());
        assertTrue(mlStatsInput.retrieveStatsOnAllNodes());
        assertTrue(mlStatsInput.retrieveAllActionLevelStats());

        mlStatsInput = new MLStatsInput();
        assertTrue(mlStatsInput.retrieveStatsForAllAlgos());
        assertTrue(mlStatsInput.retrieveStatsForAllModels());
        assertTrue(mlStatsInput.retrieveAllNodeLevelStats());
        assertTrue(mlStatsInput.retrieveStatsForAllActions());
        assertTrue(mlStatsInput.retrieveAllClusterLevelStats());
        assertTrue(mlStatsInput.retrieveStatsOnAllNodes());
        assertTrue(mlStatsInput.retrieveAllActionLevelStats());
    }

    public void testShouldRetrieveStat() {
        assertTrue(mlStatsInput.retrieveStat(MLClusterLevelStat.ML_MODEL_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLNodeLevelStat.ML_REQUEST_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        MLStatsInput mlStatsInput = MLStatsInput.builder().build();
        assertTrue(mlStatsInput.retrieveStat(MLClusterLevelStat.ML_MODEL_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLNodeLevelStat.ML_REQUEST_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        mlStatsInput = new MLStatsInput();
        assertTrue(mlStatsInput.retrieveStat(MLClusterLevelStat.ML_MODEL_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLNodeLevelStat.ML_REQUEST_COUNT));
        assertTrue(mlStatsInput.retrieveStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));

        mlStatsInput = MLStatsInput
            .builder()
            .clusterLevelStats(EnumSet.of(MLClusterLevelStat.ML_TASK_INDEX_STATUS))
            .nodeLevelStats(EnumSet.of(MLNodeLevelStat.ML_FAILURE_COUNT))
            .actionLevelStats(EnumSet.of(MLActionLevelStat.ML_ACTION_FAILURE_COUNT))
            .build();
        assertFalse(mlStatsInput.retrieveStat(MLClusterLevelStat.ML_MODEL_COUNT));
        assertFalse(mlStatsInput.retrieveStat(MLNodeLevelStat.ML_REQUEST_COUNT));
        assertFalse(mlStatsInput.retrieveStat(MLActionLevelStat.ML_ACTION_REQUEST_COUNT));
    }

    public void testOnlyRetrieveClusterLevelStats() {
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());

        MLStatsInput mlStatsInput = MLStatsInput.builder().build();
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());

        mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.CLUSTER)).build();
        assertTrue(mlStatsInput.onlyRetrieveClusterLevelStats());

        mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.NODE)).build();
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());

        mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ALGORITHM)).build();
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());

        mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.MODEL)).build();
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());

        mlStatsInput = MLStatsInput.builder().targetStatLevels(EnumSet.of(MLStatLevel.ACTION)).build();
        assertFalse(mlStatsInput.onlyRetrieveClusterLevelStats());
    }

    private void verifyParsedMLStatsInput(MLStatsInput parsedMLStatsInput) {
        assertArrayEquals(
            mlStatsInput.getTargetStatLevels().toArray(new MLStatLevel[0]),
            parsedMLStatsInput.getTargetStatLevels().toArray(new MLStatLevel[0])
        );
        assertArrayEquals(
            mlStatsInput.getClusterLevelStats().toArray(new MLClusterLevelStat[0]),
            parsedMLStatsInput.getClusterLevelStats().toArray(new MLClusterLevelStat[0])
        );
        assertArrayEquals(
            mlStatsInput.getNodeLevelStats().toArray(new MLNodeLevelStat[0]),
            parsedMLStatsInput.getNodeLevelStats().toArray(new MLNodeLevelStat[0])
        );
        assertArrayEquals(
            mlStatsInput.getActionLevelStats().toArray(new MLActionLevelStat[0]),
            parsedMLStatsInput.getActionLevelStats().toArray(new MLActionLevelStat[0])
        );
        assertArrayEquals(
            mlStatsInput.getAlgorithms().toArray(new FunctionName[0]),
            parsedMLStatsInput.getAlgorithms().toArray(new FunctionName[0])
        );
        assertArrayEquals(mlStatsInput.getModels().toArray(new String[0]), parsedMLStatsInput.getModels().toArray(new String[0]));
        assertArrayEquals(mlStatsInput.getActions().toArray(new ActionName[0]), parsedMLStatsInput.getActions().toArray(new ActionName[0]));
        assertEquals(2, parsedMLStatsInput.getNodeIds().size());
        assertTrue(parsedMLStatsInput.getNodeIds().contains(node1));
        assertTrue(parsedMLStatsInput.getNodeIds().contains(node2));
    }

}
