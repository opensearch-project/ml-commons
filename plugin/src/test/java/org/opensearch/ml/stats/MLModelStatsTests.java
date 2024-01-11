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

public class MLModelStatsTests extends OpenSearchTestCase {
    private MLModelStats mlModelStats;
    private MLActionStats mlActionStats;
    private long requestCount = 200;
    private long failureCount = 100;

    @Before
    public void setup() {
        Map<MLActionLevelStat, Object> modelActionStats = new HashMap<>();
        modelActionStats.put(ML_ACTION_REQUEST_COUNT, requestCount);
        modelActionStats.put(ML_ACTION_FAILURE_COUNT, failureCount);
        mlActionStats = new MLActionStats(modelActionStats);

        Map<ActionName, MLActionStats> modelStats = new HashMap<>();
        modelStats.put(ActionName.PREDICT, mlActionStats);
        mlModelStats = new MLModelStats(modelStats);
    }

    public void testSerializationDeserialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlModelStats.writeTo(output);
        MLModelStats parsedMLModelStats = new MLModelStats(output.bytes().streamInput());
        MLActionStats parsedMLActionStats = parsedMLModelStats.getActionStats(ActionName.PREDICT);
        assertEquals(2, parsedMLActionStats.getActionStatSize());
        assertEquals(requestCount, parsedMLActionStats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(failureCount, parsedMLActionStats.getActionStat(ML_ACTION_FAILURE_COUNT));
    }

    public void testEmptySerializationDeserialization() throws IOException {

        Map<ActionName, MLActionStats> modelStats = new HashMap<>();
        MLModelStats mlModelEmptyStats = new MLModelStats(modelStats);
        BytesStreamOutput output = new BytesStreamOutput();
        mlModelEmptyStats.writeTo(output);
        MLModelStats parsedMLModelStats = new MLModelStats(output.bytes().streamInput());
        MLActionStats parsedMLActionStats = parsedMLModelStats.getActionStats(ActionName.PREDICT);
        assertNull(parsedMLActionStats);
        // assertEquals(0, output.bytes().length());
    }

    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        mlModelStats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        Set<String> validContents = Set
            .of(
                "{\"predict\":{\"ml_action_request_count\":200,\"ml_action_failure_count\":100}}",
                "{\"predict\":{\"ml_action_failure_count\":100,\"ml_action_request_count\":200}}"
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
        assertNotNull(mlModelStats.getActionStats(ActionName.PREDICT));

        // null stats
        Map<ActionName, MLActionStats> statMap = null;
        MLModelStats stats = new MLModelStats(statMap);
        assertNull(stats.getActionStats(ActionName.PREDICT));

        // empty stats
        stats = new MLModelStats(new HashMap<>());
        assertNull(stats.getActionStats(ActionName.PREDICT));
    }
}
