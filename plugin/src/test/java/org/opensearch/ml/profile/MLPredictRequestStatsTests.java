/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.test.OpenSearchTestCase;

public class MLPredictRequestStatsTests extends OpenSearchTestCase {

    @Test
    public void testConstructorWithAllFields() {
        MLPredictRequestStats stats = MLPredictRequestStats
            .builder()
            .count(100L)
            .max(50.0)
            .min(1.0)
            .average(25.5)
            .p50(20.0)
            .p90(40.0)
            .p99(48.0)
            .build();

        assertEquals(Long.valueOf(100L), stats.getCount());
        assertEquals(Double.valueOf(50.0), stats.getMax());
        assertEquals(Double.valueOf(1.0), stats.getMin());
        assertEquals(Double.valueOf(25.5), stats.getAverage());
        assertEquals(Double.valueOf(20.0), stats.getP50());
        assertEquals(Double.valueOf(40.0), stats.getP90());
        assertEquals(Double.valueOf(48.0), stats.getP99());
    }

    @Test
    public void testConstructorWithNullFields() {
        MLPredictRequestStats stats = MLPredictRequestStats.builder().build();

        assertNull(stats.getCount());
        assertNull(stats.getMax());
        assertNull(stats.getMin());
        assertNull(stats.getAverage());
        assertNull(stats.getP50());
        assertNull(stats.getP90());
        assertNull(stats.getP99());
    }

    @Test
    public void testToXContentWithAllFields() throws IOException {
        MLPredictRequestStats stats = MLPredictRequestStats
            .builder()
            .count(100L)
            .max(50.0)
            .min(1.0)
            .average(25.5)
            .p50(20.0)
            .p90(40.0)
            .p99(48.0)
            .build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        stats.toXContent(builder, null);
        String json = builder.toString();

        assertTrue(json.contains("\"count\":100"));
        assertTrue(json.contains("\"max\":50.0"));
        assertTrue(json.contains("\"min\":1.0"));
        assertTrue(json.contains("\"average\":25.5"));
        assertTrue(json.contains("\"p50\":20.0"));
        assertTrue(json.contains("\"p90\":40.0"));
        assertTrue(json.contains("\"p99\":48.0"));
    }

    @Test
    public void testToXContentWithNullFields() throws IOException {
        MLPredictRequestStats stats = MLPredictRequestStats.builder().build();

        XContentBuilder builder = XContentFactory.jsonBuilder();
        stats.toXContent(builder, null);
        String json = builder.toString();

        assertEquals("{}", json);
    }

    @Test
    public void testStreamSerialization() throws IOException {
        MLPredictRequestStats original = MLPredictRequestStats
            .builder()
            .count(100L)
            .max(50.0)
            .min(1.0)
            .average(25.5)
            .p50(20.0)
            .p90(40.0)
            .p99(48.0)
            .build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLPredictRequestStats deserialized = new MLPredictRequestStats(input);

        assertEquals(original.getCount(), deserialized.getCount());
        assertEquals(original.getMax(), deserialized.getMax());
        assertEquals(original.getMin(), deserialized.getMin());
        assertEquals(original.getAverage(), deserialized.getAverage());
        assertEquals(original.getP50(), deserialized.getP50());
        assertEquals(original.getP90(), deserialized.getP90());
        assertEquals(original.getP99(), deserialized.getP99());
    }

    @Test
    public void testStreamSerializationWithNulls() throws IOException {
        MLPredictRequestStats original = MLPredictRequestStats.builder().build();

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        MLPredictRequestStats deserialized = new MLPredictRequestStats(input);

        assertNull(deserialized.getCount());
        assertNull(deserialized.getMax());
        assertNull(deserialized.getMin());
        assertNull(deserialized.getAverage());
        assertNull(deserialized.getP50());
        assertNull(deserialized.getP90());
        assertNull(deserialized.getP99());
    }
}
