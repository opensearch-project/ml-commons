/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.ml.common.TestHelper;

public class RetentionRuleTests {

    @Test
    public void testBothFieldsSet() {
        RetentionRule rule = new RetentionRule(30, 100);
        assertEquals(Integer.valueOf(30), rule.getRetentionDays());
        assertEquals(Integer.valueOf(100), rule.getMaxCount());
    }

    @Test
    public void testOnlyRetentionDaysSet() {
        RetentionRule rule = new RetentionRule(7, null);
        assertEquals(Integer.valueOf(7), rule.getRetentionDays());
        assertNull(rule.getMaxCount());
    }

    @Test
    public void testOnlyMaxCountSet() {
        RetentionRule rule = new RetentionRule(null, 50);
        assertNull(rule.getRetentionDays());
        assertEquals(Integer.valueOf(50), rule.getMaxCount());
    }

    @Test
    public void testBothNull() {
        RetentionRule rule = new RetentionRule(null, null);
        assertNull(rule.getRetentionDays());
        assertNull(rule.getMaxCount());
    }

    @Test
    public void testNegativeRetentionDaysThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new RetentionRule(-1, null));
        assertEquals("retention_days must be a positive integer or null", e.getMessage());
    }

    @Test
    public void testZeroRetentionDaysThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new RetentionRule(0, null));
        assertEquals("retention_days must be a positive integer or null", e.getMessage());
    }

    @Test
    public void testNegativeMaxCountThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new RetentionRule(null, -5));
        assertEquals("max_count must be a positive integer or null", e.getMessage());
    }

    @Test
    public void testZeroMaxCountThrows() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new RetentionRule(null, 0));
        assertEquals("max_count must be a positive integer or null", e.getMessage());
    }

    @Test
    public void testXContentRoundTrip_BothFields() throws IOException {
        RetentionRule original = new RetentionRule(30, 100);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertEquals(original, parsed);
    }

    @Test
    public void testXContentRoundTrip_OnlyRetentionDays() throws IOException {
        RetentionRule original = new RetentionRule(14, null);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertEquals(original, parsed);
    }

    @Test
    public void testXContentRoundTrip_OnlyMaxCount() throws IOException {
        RetentionRule original = new RetentionRule(null, 200);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertEquals(original, parsed);
    }

    @Test
    public void testXContentRoundTrip_BothNull() throws IOException {
        RetentionRule original = new RetentionRule(null, null);

        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = TestHelper.xContentBuilderToString(builder);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertEquals(original, parsed);
    }

    @Test
    public void testStreamRoundTrip_BothFields() throws IOException {
        RetentionRule original = new RetentionRule(30, 100);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RetentionRule deserialized = new RetentionRule(input);

        assertEquals(original, deserialized);
    }

    @Test
    public void testStreamRoundTrip_OnlyRetentionDays() throws IOException {
        RetentionRule original = new RetentionRule(7, null);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RetentionRule deserialized = new RetentionRule(input);

        assertEquals(original, deserialized);
    }

    @Test
    public void testStreamRoundTrip_OnlyMaxCount() throws IOException {
        RetentionRule original = new RetentionRule(null, 50);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RetentionRule deserialized = new RetentionRule(input);

        assertEquals(original, deserialized);
    }

    @Test
    public void testStreamRoundTrip_BothNull() throws IOException {
        RetentionRule original = new RetentionRule(null, null);

        BytesStreamOutput output = new BytesStreamOutput();
        original.writeTo(output);

        StreamInput input = output.bytes().streamInput();
        RetentionRule deserialized = new RetentionRule(input);

        assertEquals(original, deserialized);
    }

    @Test
    public void testParseFromJsonString() throws IOException {
        String json = "{\"retention_days\":60,\"max_count\":500}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertNotNull(parsed);
        assertEquals(Integer.valueOf(60), parsed.getRetentionDays());
        assertEquals(Integer.valueOf(500), parsed.getMaxCount());
    }

    @Test
    public void testParseFromJsonString_UnknownFieldsIgnored() throws IOException {
        String json = "{\"retention_days\":10,\"max_count\":20,\"unknown_field\":\"ignored\"}";

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        RetentionRule parsed = RetentionRule.parse(parser);

        assertEquals(Integer.valueOf(10), parsed.getRetentionDays());
        assertEquals(Integer.valueOf(20), parsed.getMaxCount());
    }

    @Test
    public void testBuilder() {
        RetentionRule rule = RetentionRule.builder().retentionDays(14).maxCount(50).build();
        assertEquals(Integer.valueOf(14), rule.getRetentionDays());
        assertEquals(Integer.valueOf(50), rule.getMaxCount());
    }

    @Test
    public void testEquality() {
        RetentionRule rule1 = new RetentionRule(30, 100);
        RetentionRule rule2 = new RetentionRule(30, 100);
        assertEquals(rule1, rule2);
        assertEquals(rule1.hashCode(), rule2.hashCode());
    }
}
