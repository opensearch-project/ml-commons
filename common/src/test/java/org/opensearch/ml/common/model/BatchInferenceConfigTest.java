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
    public void defaultsAppliedWhenFieldsOmitted() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().build();
        assertEquals(BatchInferenceConfig.DEFAULT_MAX_ITEMS_PER_REQUEST, config.getMaxItemsPerRequest());
        assertEquals(BatchInferenceConfig.DISABLED_MAX_BYTES_PER_REQUEST, config.getMaxBytesPerRequest());
        assertFalse(config.isByteLimitEnabled());
    }

    @Test
    public void byteLimitEnabledWhenPositive() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(96).maxBytesPerRequest(4_000_000L).build();
        assertEquals(96, config.getMaxItemsPerRequest());
        assertEquals(4_000_000L, config.getMaxBytesPerRequest());
        assertTrue(config.isByteLimitEnabled());
    }

    @Test
    public void rejectsNonPositiveItemLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_items_per_request");
        BatchInferenceConfig.builder().maxItemsPerRequest(0).build();
    }

    @Test
    public void rejectsInvalidByteLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_bytes_per_request");
        BatchInferenceConfig.builder().maxBytesPerRequest(0L).build();
    }

    @Test
    public void disabledByteLimitIsValid() {
        BatchInferenceConfig config = BatchInferenceConfig.builder().maxItemsPerRequest(10).maxBytesPerRequest(-1L).build();
        assertFalse(config.isByteLimitEnabled());
        assertEquals(-1L, config.getMaxBytesPerRequest());
    }

    @Test
    public void rejectsNegativeByteLimitOtherThanDisabled() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_bytes_per_request");
        BatchInferenceConfig.builder().maxBytesPerRequest(-2L).build();
    }

    @Test
    public void rejectsNegativeItemLimit() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("max_items_per_request");
        BatchInferenceConfig.builder().maxItemsPerRequest(-5).build();
    }

    @Test
    public void streamRoundTripWithDisabledByteLimit() throws IOException {
        BatchInferenceConfig original = BatchInferenceConfig.builder().maxItemsPerRequest(7).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        BatchInferenceConfig restored = new BatchInferenceConfig(out.bytes().streamInput());
        assertEquals(7, restored.getMaxItemsPerRequest());
        assertEquals(BatchInferenceConfig.DISABLED_MAX_BYTES_PER_REQUEST, restored.getMaxBytesPerRequest());
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
}
