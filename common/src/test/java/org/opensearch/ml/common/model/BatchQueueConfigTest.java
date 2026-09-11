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
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class BatchQueueConfigTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void defaultsToDisabledWithDefaultTimeout() {
        BatchQueueConfig config = BatchQueueConfig.builder().build();
        assertFalse(config.isEnabled());
        assertEquals(BatchQueueConfig.DEFAULT_FLUSH_TIMEOUT_MS, config.getFlushTimeoutMs());
    }

    @Test
    public void enabledWithExplicitTimeout() {
        BatchQueueConfig config = BatchQueueConfig.builder().enabled(true).flushTimeoutMs(10L).build();
        assertTrue(config.isEnabled());
        assertEquals(10L, config.getFlushTimeoutMs());
    }

    @Test
    public void rejectsNonPositiveFlushTimeout() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("flush_timeout_ms");
        BatchQueueConfig.builder().enabled(true).flushTimeoutMs(0L).build();
    }

    @Test
    public void rejectsFlushTimeoutAboveMax() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("flush_timeout_ms");
        BatchQueueConfig.builder().enabled(true).flushTimeoutMs(BatchQueueConfig.MAX_FLUSH_TIMEOUT_MS + 1).build();
    }

    @Test
    public void streamRoundTrip() throws IOException {
        BatchQueueConfig original = BatchQueueConfig.builder().enabled(true).flushTimeoutMs(100L).build();
        BytesStreamOutput out = new BytesStreamOutput();
        original.writeTo(out);
        BatchQueueConfig restored = new BatchQueueConfig(out.bytes().streamInput());
        assertTrue(restored.isEnabled());
        assertEquals(100L, restored.getFlushTimeoutMs());
    }

    @Test
    public void xContentRoundTrip() throws IOException {
        BatchQueueConfig original = BatchQueueConfig.builder().enabled(true).flushTimeoutMs(50L).build();
        XContentBuilder builder = XContentType.JSON.contentBuilder();
        original.toXContent(builder, ToXContent.EMPTY_PARAMS);

        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, builder.toString());
        parser.nextToken();
        BatchQueueConfig parsed = BatchQueueConfig.parse(parser);
        assertTrue(parsed.isEnabled());
        assertEquals(50L, parsed.getFlushTimeoutMs());
    }

    @Test
    public void parsesOmittedTimeoutAsDefault() throws IOException {
        String json = "{\"enabled\":true}";
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, json);
        parser.nextToken();
        BatchQueueConfig parsed = BatchQueueConfig.parse(parser);
        assertTrue(parsed.isEnabled());
        assertEquals(BatchQueueConfig.DEFAULT_FLUSH_TIMEOUT_MS, parsed.getFlushTimeoutMs());
    }
}
