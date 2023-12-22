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
import org.junit.Ignore;
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

    private MLRateLimiter rateLimiterWithNumber;

    private MLRateLimiter rateLimiterWithUnit;

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
        rateLimiterWithNumber = MLRateLimiter.builder()
                .rateLimitNumber("1")
                .build();

        rateLimiterWithUnit = MLRateLimiter.builder()
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
        readInputStream(rateLimiterWithNumber, parsedInput -> {
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
        assertTrue(rateLimiter.isRateLimiterConstructable());
        assertFalse(rateLimiterWithNumber.isRateLimiterConstructable());
        assertFalse(rateLimiterWithUnit.isRateLimiterConstructable());
        assertFalse(rateLimiterNull.isRateLimiterConstructable());
    }

    @Test
    public void testIsRateLimiterRemovable() {
        assertFalse(rateLimiter.isRateLimiterEmpty());
        assertFalse(rateLimiterWithNumber.isRateLimiterEmpty());
        assertFalse(rateLimiterWithUnit.isRateLimiterEmpty());
        assertTrue(rateLimiterNull.isRateLimiterEmpty());
    }

    @Test
    public void testRateLimiterUpdate() {
        MLRateLimiter updatedRateLimiter = MLRateLimiter.update(rateLimiterNull, rateLimiter);
        assertEquals("1", updatedRateLimiter.getRateLimitNumber());
        assertEquals(TimeUnit.MILLISECONDS, updatedRateLimiter.getRateLimitUnit());
    }

    @Test
    public void testRateLimiterPartiallyUpdate() {
        rateLimiterNull.update(rateLimiterWithNumber);
        assertEquals("1", rateLimiterNull.getRateLimitNumber());
        assertNull(rateLimiterNull.getRateLimitUnit());
        rateLimiterNull.update(rateLimiterWithUnit);
        assertEquals("1", rateLimiterNull.getRateLimitNumber());
        assertEquals(TimeUnit.MILLISECONDS, rateLimiterNull.getRateLimitUnit());
    }

    @Test
    public void testRateLimiterUpdateNull() {
        MLRateLimiter updatedRateLimiter = MLRateLimiter.update(null, rateLimiter);
        assertEquals("1", updatedRateLimiter.getRateLimitNumber());
        assertEquals(TimeUnit.MILLISECONDS, updatedRateLimiter.getRateLimitUnit());
    }

    @Test
    public void testRateLimiterIsUpdatable() {
        assertFalse(MLRateLimiter.isUpdatable(rateLimiter, null));
        assertFalse(MLRateLimiter.isUpdatable(rateLimiter, rateLimiterNull));

        assertTrue(MLRateLimiter.isUpdatable(null, rateLimiter));
        assertTrue(MLRateLimiter.isUpdatable(rateLimiterNull, rateLimiter));

        assertTrue(MLRateLimiter.isUpdatable(rateLimiterWithUnit, rateLimiterWithNumber));
        assertTrue(MLRateLimiter.isUpdatable(rateLimiterWithUnit, rateLimiter));
        assertTrue(MLRateLimiter.isUpdatable(rateLimiterWithNumber, rateLimiter));
        assertFalse(MLRateLimiter.isUpdatable(rateLimiter, rateLimiter));

        assertFalse(MLRateLimiter.isUpdatable(rateLimiter, rateLimiterWithUnit));
        assertFalse(MLRateLimiter.isUpdatable(rateLimiter, rateLimiterWithNumber));
    }

    @Test
    public void testRateLimiterIsDeployRequiredAfterUpdate() {
        MLRateLimiter rateLimiterWithNumber2 = MLRateLimiter.builder()
                .rateLimitNumber("2")
                .build();

        MLRateLimiter rateLimiterWithUnit2 = MLRateLimiter.builder()
                .rateLimitUnit(TimeUnit.NANOSECONDS)
                .build();

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiter, rateLimiterWithNumber2));

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterNull, rateLimiter));

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithUnit, rateLimiterWithNumber));
        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithNumber, rateLimiterWithUnit));
        assertFalse(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithUnit, rateLimiterWithUnit2));
        assertFalse(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithNumber, rateLimiterWithNumber2));
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

    @Ignore
    @Test
    public void testRateLimiterRemove() {
        MLRateLimiter updatedRateLimiter = MLRateLimiter.update(rateLimiter, rateLimiterNull);
        assertNull(updatedRateLimiter.getRateLimitUnit());
        assertNull(updatedRateLimiter.getRateLimitNumber());
    }
}
