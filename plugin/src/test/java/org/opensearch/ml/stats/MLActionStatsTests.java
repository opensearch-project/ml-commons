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

public class MLActionStatsTests extends OpenSearchTestCase {

    private MLActionStats mlActionStats;
    private long requestCount = 200;
    private long failureCount = 100;

    @Before
    public void setup() {
        Map<MLActionLevelStat, Object> algoActionStats = new HashMap<>();
        algoActionStats.put(ML_ACTION_REQUEST_COUNT, requestCount);
        algoActionStats.put(ML_ACTION_FAILURE_COUNT, failureCount);
        mlActionStats = new MLActionStats(algoActionStats);
    }

    public void testSerializationDeserialization() throws IOException {
        BytesStreamOutput output = new BytesStreamOutput();
        mlActionStats.writeTo(output);
        MLActionStats parsedMLActionStats = new MLActionStats(output.bytes().streamInput());
        assertEquals(2, parsedMLActionStats.getActionStatSize());
        assertEquals(requestCount, parsedMLActionStats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(failureCount, parsedMLActionStats.getActionStat(ML_ACTION_FAILURE_COUNT));
    }

    public void testToXContent() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        mlActionStats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        Set<String> validContents = Set
            .of(
                "{\"ml_action_request_count\":200,\"ml_action_failure_count\":100}",
                "{\"ml_action_failure_count\":100,\"ml_action_request_count\":200}"
            );
        assertTrue(validContents.contains(content));
    }

    public void testToXContent_EmptyActionStats() throws IOException {
        Map<MLActionLevelStat, Object> statMap = new HashMap<>();
        MLActionStats stats = new MLActionStats(statMap);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        stats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", content);
    }

    public void testToXContent_NullActionStats() throws IOException {
        Map<MLActionLevelStat, Object> statMap = null;
        MLActionStats stats = new MLActionStats(statMap);
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        stats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        assertEquals("{}", content);
    }

    public void testGetActionStat_NullActionStats() {
        Map<MLActionLevelStat, Object> statMap = null;
        MLActionStats stats = new MLActionStats(statMap);
        assertNull(stats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(0, stats.getActionStatSize());
    }

    public void testGetActionStat_EmptyActionStats() {
        assertNotNull(mlActionStats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(2, mlActionStats.getActionStatSize());

        Map<MLActionLevelStat, Object> statMap = new HashMap<>();
        MLActionStats stats = new MLActionStats(statMap);
        assertNull(stats.getActionStat(ML_ACTION_REQUEST_COUNT));
        assertEquals(0, stats.getActionStatSize());
    }

}
