/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import static org.opensearch.core.xcontent.ToXContent.EMPTY_PARAMS;
import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_FAILURE_COUNT;
import static org.opensearch.ml.stats.MLActionLevelStat.ML_ACTION_REQUEST_COUNT;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.opensearch.Version;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;
import org.opensearch.ml.utils.TestHelper;
import org.opensearch.test.OpenSearchTestCase;

import com.google.common.collect.ImmutableSet;

public class MLModelStatsTests extends OpenSearchTestCase {
    private MLModelStats mlModelStats;
    private MLActionStats mlActionStats;
    private long requestCount = 200;
    private long failureCount = 100;

    Map<ActionName, MLActionStats> modelStats = new HashMap<>();

    @Before
    public void setup() {
        Map<MLActionLevelStat, Object> modelActionStats = new HashMap<>();
        modelActionStats.put(ML_ACTION_REQUEST_COUNT, requestCount);
        modelActionStats.put(ML_ACTION_FAILURE_COUNT, failureCount);
        mlActionStats = new MLActionStats(modelActionStats);
        modelStats.put(ActionName.PREDICT, mlActionStats);
        mlModelStats = new MLModelStats(modelStats, false);
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
        MLModelStats mlModelEmptyStats = new MLModelStats(modelStats, false);
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
        mlModelStats = new MLModelStats(modelStats, false);
        mlModelStats.toXContent(builder, EMPTY_PARAMS);
        builder.endObject();
        String content = TestHelper.xContentBuilderToString(builder);
        Set<String> validContents = ImmutableSet
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
        MLModelStats stats = new MLModelStats(statMap, false);
        assertNull(stats.getActionStats(ActionName.PREDICT));

        // empty stats
        stats = new MLModelStats(new HashMap<>(), false);
        assertNull(stats.getActionStats(ActionName.PREDICT));
    }

    public void testVersionBasedSerialization() throws IOException {
        Version oldVersion = Version.fromString("1.0.0"); // below the threshold
        Version newVersion = MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK; // at or above threshold

        BytesStreamOutput output = new BytesStreamOutput();
        output.setVersion(newVersion);
        mlModelStats.writeTo(output);
        StreamInput input = output.bytes().streamInput();
        input.setVersion(newVersion);
        MLModelStats newVersionStats = new MLModelStats(input);
        assertEquals(mlModelStats.getIsHidden(), newVersionStats.getIsHidden());

        output = new BytesStreamOutput();
        output.setVersion(oldVersion);
        mlModelStats.writeTo(output);
        input = output.bytes().streamInput();
        input.setVersion(oldVersion);
        MLModelStats oldVersionStats = new MLModelStats(input);
        assertNull(oldVersionStats.getIsHidden()); // Assuming `isHidden` is not serialized for old versions
    }

    public void testToXContentWithParams() throws IOException {
        XContentBuilder builder = XContentBuilder.builder(XContentType.JSON.xContent());
        builder.startObject();
        ToXContent.Params params = new ToXContent.MapParams(Collections.singletonMap("pretty", "true"));
        mlModelStats.toXContent(builder, params);
        builder.endObject();
        String prettyContent = TestHelper.xContentBuilderToString(builder);
        assertNotNull(prettyContent);
        assertFalse(prettyContent.contains("\n"));
    }

}
