/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class BatchInferenceConfigTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void rejectsBothLimitsOmitted() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("must enable at least one limit");
        BatchInferenceConfig.builder().build();
    }

    @Test
    public void rejectsBothLimitsExplicitlyDisabled() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("must enable at least one limit");
        BatchInferenceConfig.builder().maxItemsPerRequest(-1).maxBytesPerRequest(-1L).build();
    }

    @Test
    public void itemLimitOnlyIsValid() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(96).build();
        assertEquals(96, config.getMaxItemsPerRequest());
        assertEquals(BatchInferenceConfig.NO_LIMIT, config.getMaxBytesPerRequest());
        assertTrue(config.isItemLimitEnabled());
        assertFalse(config.isByteLimitEnabled());
    }

    @Test
    public void byteLimitOnlyIsValid() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxBytesPerRequest(4_000_000L).build();
        assertEquals(BatchInferenceConfig.NO_LIMIT, config.getMaxItemsPerRequest());
        assertEquals(4_000_000L, config.getMaxBytesPerRequest());
        assertFalse(config.isItemLimitEnabled());
        assertTrue(config.isByteLimitEnabled());
    }

    @Test
    public void bothLimitsEnabledIsValid() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(96).maxBytesPerRequest(4_000_000L).build();
        assertEquals(96, config.getMaxItemsPerRequest());
        assertEquals(4_000_000L, config.getMaxBytesPerRequest());
        assertTrue(config.isItemLimitEnabled());
        assertTrue(config.isByteLimitEnabled());
    }

    @Test
    public void rejectsNonPositiveItemLimitOtherThanNoLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_items_per_request");
        BatchInferenceConfig.builder().maxItemsPerRequest(0).build();
    }

    @Test
    public void rejectsNonPositiveByteLimitOtherThanNoLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_bytes_per_request");
        BatchInferenceConfig.builder().maxBytesPerRequest(0L).build();
    }

    @Test
    public void rejectsNegativeItemLimitOtherThanNoLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_items_per_request");
        BatchInferenceConfig.builder().maxItemsPerRequest(-5).build();
    }

    @Test
    public void rejectsNegativeByteLimitOtherThanNoLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_bytes_per_request");
        BatchInferenceConfig.builder().maxItemsPerRequest(10).maxBytesPerRequest(-2L).build();
    }

    @Test
    public void streamRoundTripWithDisabledByteLimit() throws IOException {
        BatchInferenceConfig original = BatchInferenceConfig.builder().maxItemsPerRequest(7).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        BatchInferenceConfig restored = new BatchInferenceConfig(out.bytes().streamInput());
        assertEquals(7, restored.getMaxItemsPerRequest());
        assertEquals(BatchInferenceConfig.NO_LIMIT, restored.getMaxBytesPerRequest());
    }

    @Test
    public void streamRoundTripWithDisabledItemLimit() throws IOException {
        BatchInferenceConfig original = BatchInferenceConfig.builder().maxBytesPerRequest(2048L).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        BatchInferenceConfig restored = new BatchInferenceConfig(out.bytes().streamInput());
        assertEquals(BatchInferenceConfig.NO_LIMIT, restored.getMaxItemsPerRequest());
        assertEquals(2048L, restored.getMaxBytesPerRequest());
    }

    @Test
    public void streamRoundTrip() throws IOException {
        BatchInferenceConfig original = BatchInferenceConfig.builder().maxItemsPerRequest(48).maxBytesPerRequest(1024L).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        StreamInput in = out.bytes().streamInput();
        BatchInferenceConfig restored = new BatchInferenceConfig(in);
        assertEquals(original.getMaxItemsPerRequest(), restored.getMaxItemsPerRequest());
        assertEquals(original.getMaxBytesPerRequest(), restored.getMaxBytesPerRequest());
    }

    @Test
    public void xContentRoundTrip() throws IOException {
        BatchInferenceConfig original = BatchInferenceConfig.builder().maxItemsPerRequest(96).maxBytesPerRequest(2048L).build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);
        String json = builder.toString();

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        BatchInferenceConfig parsed = BatchInferenceConfig.parse(parser);
        assertEquals(96, parsed.getMaxItemsPerRequest());
        assertEquals(2048L, parsed.getMaxBytesPerRequest());
    }

    @Test
    public void parsesOmittedItemLimitAsDisabled() throws IOException {
        String json = "{\"max_bytes_per_request\":4096}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        BatchInferenceConfig parsed = BatchInferenceConfig.parse(parser);
        assertEquals(BatchInferenceConfig.NO_LIMIT, parsed.getMaxItemsPerRequest());
        assertEquals(4096L, parsed.getMaxBytesPerRequest());
    }
}
