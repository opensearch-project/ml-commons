/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.common.controller;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchModule;

public class MLRateLimiterTest {

    private MLRateLimiter rateLimiter;

    private MLRateLimiter rateLimiterNullUnit;

    private MLRateLimiter rateLimiterNullNumber;

    private MLRateLimiter rateLimiterNull;

    private final String expectedInputStr = "{\"rate_limit_number\":\"1\",\"rate_limit_unit\":\"MILLISECONDS\"}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        rateLimiter = MLRateLimiter.builder()
                .rateLimitNumber("1")
                .rateLimitUnit(TimeUnit.MILLISECONDS)
                .build();
        rateLimiterNullUnit = MLRateLimiter.builder()
                .rateLimitNumber("1")
                .build();

        rateLimiterNullNumber = MLRateLimiter.builder()
                .rateLimitUnit(TimeUnit.MILLISECONDS)
                .build();

        rateLimiterNull = MLRateLimiter.builder().build();

    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(rateLimiter, parsedInput -> {
            assertEquals("1", parsedInput.getRateLimitNumber());
            assertEquals(TimeUnit.MILLISECONDS, parsedInput.getRateLimitUnit());
        });
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        readInputStream(rateLimiterNullUnit, parsedInput -> {
            assertNull(parsedInput.getRateLimitUnit());
        });
    }

    @Test
    public void testToXContent() throws Exception {
        String jsonStr = serializationWithToXContent(rateLimiter);
        assertEquals(expectedInputStr, jsonStr);
    }

    @Test
    public void testToXContentIncomplete() throws Exception {
        final String expectedIncompleteInputStr = "{}";

        String jsonStr = serializationWithToXContent(rateLimiterNull);
        assertEquals(expectedIncompleteInputStr, jsonStr);
    }

    @Test
    public void parseSuccess() throws Exception {
        testParseFromJsonString(expectedInputStr, parsedInput -> {
            assertEquals("1", parsedInput.getRateLimitNumber());
            assertEquals(TimeUnit.MILLISECONDS, parsedInput.getRateLimitUnit());
        });
    }

    @Test
    public void parseWithNullField() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        final String expectedInputStrWithNullField = "{\"rate_limit_number\":\"1\",\"rate_limit_unit\":null}";

        testParseFromJsonString(expectedInputStrWithNullField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void parseWithIllegalField() throws Exception {
        final String expectedInputStrWithIllegalField = "{\"rate_limit_number\":\"1\",\"rate_limit_unit\":" +
                "\"MILLISECONDS\",\"illegal_field\":\"This field need to be skipped.\"}";

        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testIsRateLimiterConstructable() {
        assertTrue(MLRateLimiter.isRateLimiterConstructable(rateLimiter));
        assertFalse(MLRateLimiter.isRateLimiterConstructable(rateLimiterNullUnit));
        assertFalse(MLRateLimiter.isRateLimiterConstructable(rateLimiterNullNumber));
        assertFalse(MLRateLimiter.isRateLimiterConstructable(rateLimiterNull));
    }

    @Test
    public void testIsRateLimiterRemovable() {
        assertFalse(MLRateLimiter.isRateLimiterRemovable(rateLimiter));
        assertFalse(MLRateLimiter.isRateLimiterRemovable(rateLimiterNullUnit));
        assertFalse(MLRateLimiter.isRateLimiterRemovable(rateLimiterNullNumber));
        assertTrue(MLRateLimiter.isRateLimiterRemovable(rateLimiterNull));
    }

    @Test
    public void testRateLimiterUpdate() {
        rateLimiter.update(rateLimiterNull);
        assertEquals(TimeUnit.MILLISECONDS, rateLimiter.getRateLimitUnit());
        assertEquals("1", rateLimiter.getRateLimitNumber());
        rateLimiterNull.update(rateLimiter);
        assertEquals("1", rateLimiterNull.getRateLimitNumber());
        assertEquals(TimeUnit.MILLISECONDS, rateLimiterNull.getRateLimitUnit());
    }

    @Test
    public void testRateLimiterCanUpdate() {
        assertTrue(MLRateLimiter.canUpdate(null, rateLimiter));
        assertTrue(MLRateLimiter.canUpdate(rateLimiterNull, rateLimiter));
        assertFalse(MLRateLimiter.canUpdate(rateLimiter, null));
        assertFalse(MLRateLimiter.canUpdate(rateLimiter, rateLimiterNull));
        assertTrue(MLRateLimiter.canUpdate(rateLimiterNullNumber, rateLimiterNullUnit));
        assertTrue(MLRateLimiter.canUpdate(rateLimiterNullNumber, rateLimiter));
        assertTrue(MLRateLimiter.canUpdate(rateLimiterNullUnit, rateLimiter));
        assertFalse(MLRateLimiter.canUpdate(rateLimiter, rateLimiter));
    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLRateLimiter> verify) throws Exception {
        XContentParser parser = XContentType.JSON.xContent().createParser(new NamedXContentRegistry(new SearchModule(Settings.EMPTY,
                Collections.emptyList()).getNamedXContents()), LoggingDeprecationHandler.INSTANCE, expectedInputStr);
        parser.nextToken();
        MLRateLimiter parsedInput = MLRateLimiter.parse(parser);
        verify.accept(parsedInput);
    }

    private void readInputStream(MLRateLimiter input, Consumer<MLRateLimiter> verify) throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        input.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        MLRateLimiter parsedInput = new MLRateLimiter(streamInput);
        verify.accept(parsedInput);
    }

    private String serializationWithToXContent(MLRateLimiter input) throws IOException {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        input.toXContent(builder, ToXContent.EMPTY_PARAMS);
        assertNotNull(builder);
        return builder.toString();
    }
}
