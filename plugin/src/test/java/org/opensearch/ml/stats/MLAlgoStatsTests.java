/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_FAILURE_COUNT;
import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_REQUEST_COUNT;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

public class MLAlgoStatsTests extends OpenSearchTestCase {
    private MLAlgoStats mlAlgoStats;
    private MLActionStats mlActionStats;
    private long requestCount = 200;
    private long failureCount = 100;

    @Before
    public void setup() {
        Map<MLActionLevelStat, Object> algoActionStats = new HashMap<>();
        algoActionStats.put(ML_ACTION_REQUEST_COUNT, requestCount);
        algoActionStats.put(ML_ACTION_FAILURE_COUNT, failureCount);
        mlActionStats = new MLActionStats(algoActionStats);

        Map<ActionName, MLActionStats> algoStats = new HashMap<>();
        algoStats.put(ActionName.TRAIN, mlActionStats);
        mlAlgoStats = new MLAlgoStats(algoStats);
    }

    public void testSerializationDeserialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlAlgoStats.writeTo(output);
        MLAlgoStats parsedMLAlgoStats = new MLAlgoStats(output.bytes().streamInput());
        MLActionStats parsedMLActionStats = parsedMLAlgoStats.getActionStats(ActionName.TRAIN);
        assertEquals(2, parsedMLActionStats.getActionStatSize());
        assertEquals(requestCount, parsedMLActionStats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(failureCount, parsedMLActionStats.getActionStat(ML_ACTION_FAILURE_COUNT));
    }

    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        mlAlgoStats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        Set<String> validContents = Set
            .of(
                "{\"train\":{\"ml_action_request_count\":200,\"ml_action_failure_count\":100}}",
                "{\"train\":{\"ml_action_failure_count\":100,\"ml_action_request_count\":200}}"
            );
        assertTrue(validContents.contains(content));
    }

    public void testToXContent_EmptyStats() throws IOException {
        Map<ActionName, MLActionStats> statMap = new HashMap<>();
        MLAlgoStats stats = new MLAlgoStats(statMap);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        stats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", content);
    }

    public void testToXContent_NullStats() throws IOException {
        Map<ActionName, MLActionStats> statMap = null;
        MLAlgoStats stats = new MLAlgoStats(statMap);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        stats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", content);
    }

    public void testGetActionStats() {
        assertNotNull(mlAlgoStats.getActionStats(ActionName.TRAIN));

        // null stats
        Map<ActionName, MLActionStats> statMap = null;
        MLAlgoStats stats = new MLAlgoStats(statMap);
        assertNull(stats.getActionStats(ActionName.TRAIN));

        // empty stats
        stats = new MLAlgoStats(new HashMap<>());
        assertNull(stats.getActionStats(ActionName.TRAIN));
    }
}
