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
import org.opensearch.OpenSearchParseException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
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

    private final String expectedInputStr = "{\"limit\":\"1\",\"unit\":\"MILLISECONDS\"}";

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        rateLimiter = MLRateLimiter.builder().limit("1").unit(TimeUnit.MILLISECONDS).build();

        rateLimiterWithNumber = MLRateLimiter.builder().limit("1").build();

        rateLimiterWithUnit = MLRateLimiter.builder().unit(TimeUnit.MILLISECONDS).build();

        rateLimiterNull = MLRateLimiter.builder().build();

    }

    @Test
    public void readInputStreamSuccess() throws IOException {
        readInputStream(rateLimiter, parsedInput -> {
            assertEquals("1", parsedInput.getLimit());
            assertEquals(TimeUnit.MILLISECONDS, parsedInput.getUnit());
        });
    }

    @Test
    public void readInputStreamSuccessWithNullFields() throws IOException {
        readInputStream(rateLimiterWithNumber, parsedInput -> { assertNull(parsedInput.getUnit()); });
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
            assertEquals("1", parsedInput.getLimit());
            assertEquals(TimeUnit.MILLISECONDS, parsedInput.getUnit());
        });
    }

    @Test
    public void parseWithIllegalLimit() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        String inputStrWithIllegalLimit = "{\"limit\":\"-1\",\"unit\":\"MILLISECONDS\"}";
        testParseFromJsonString(inputStrWithIllegalLimit, parsedInput -> {});
    }

    @Test
    public void parseWithNegativeLimit() throws Exception {
        exceptionRule.expect(OpenSearchParseException.class);
        String inputStrWithNegativeLimit = "{\"limit\":\"ILLEGAL\",\"unit\":\"MILLISECONDS\"}";
        testParseFromJsonString(inputStrWithNegativeLimit, parsedInput -> {});
    }

    @Test
    public void parseWithNullField() throws Exception {
        exceptionRule.expect(IllegalStateException.class);
        final String expectedInputStrWithNullField = "{\"limit\":\"1\",\"unit\":null}";

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
        final String expectedInputStrWithIllegalField = "{\"limit\":\"1\",\"unit\":"
            + "\"MILLISECONDS\",\"illegal_field\":\"This field need to be skipped.\"}";

        testParseFromJsonString(expectedInputStrWithIllegalField, parsedInput -> {
            try {
                assertEquals(expectedInputStr, serializationWithToXContent(parsedInput));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    public void testisValid() {
        assertTrue(rateLimiter.isValid());
        assertFalse(rateLimiterWithNumber.isValid());
        assertFalse(rateLimiterWithUnit.isValid());
        assertFalse(rateLimiterNull.isValid());
    }

    @Test
    public void testIsRateLimiterRemovable() {
        assertFalse(rateLimiter.isEmpty());
        assertFalse(rateLimiterWithNumber.isEmpty());
        assertFalse(rateLimiterWithUnit.isEmpty());
        assertTrue(rateLimiterNull.isEmpty());
    }

    @Test
    public void testRateLimiterUpdate() {
        MLRateLimiter updatedRateLimiter = MLRateLimiter.update(rateLimiterNull, rateLimiter);
        assertEquals("1", updatedRateLimiter.getLimit());
        assertEquals(TimeUnit.MILLISECONDS, updatedRateLimiter.getUnit());
    }

    @Test
    public void testRateLimiterPartiallyUpdate() {
        rateLimiterNull.update(rateLimiterWithNumber);
        assertEquals("1", rateLimiterNull.getLimit());
        assertNull(rateLimiterNull.getUnit());
        rateLimiterNull.update(rateLimiterWithUnit);
        assertEquals("1", rateLimiterNull.getLimit());
        assertEquals(TimeUnit.MILLISECONDS, rateLimiterNull.getUnit());
    }

    @Test
    public void testRateLimiterUpdateNull() {
        MLRateLimiter updatedRateLimiter = MLRateLimiter.update(null, rateLimiter);
        assertEquals("1", updatedRateLimiter.getLimit());
        assertEquals(TimeUnit.MILLISECONDS, updatedRateLimiter.getUnit());
    }

    @Test
    public void testRateLimiterIsUpdatable() {
        assertFalse(MLRateLimiter.updateValidityPreCheck(rateLimiter, null));
        assertFalse(MLRateLimiter.updateValidityPreCheck(rateLimiter, rateLimiterNull));

        assertTrue(MLRateLimiter.updateValidityPreCheck(null, rateLimiter));
        assertTrue(MLRateLimiter.updateValidityPreCheck(rateLimiterNull, rateLimiter));

        assertTrue(MLRateLimiter.updateValidityPreCheck(rateLimiterWithUnit, rateLimiterWithNumber));
        assertTrue(MLRateLimiter.updateValidityPreCheck(rateLimiterWithUnit, rateLimiter));
        assertTrue(MLRateLimiter.updateValidityPreCheck(rateLimiterWithNumber, rateLimiter));
        assertFalse(MLRateLimiter.updateValidityPreCheck(rateLimiter, rateLimiter));

        assertFalse(MLRateLimiter.updateValidityPreCheck(rateLimiter, rateLimiterWithUnit));
        assertFalse(MLRateLimiter.updateValidityPreCheck(rateLimiter, rateLimiterWithNumber));
    }

    @Test
    public void testRateLimiterIsDeployRequiredAfterUpdate() {
        MLRateLimiter rateLimiterWithNumber2 = MLRateLimiter.builder().limit("2").build();

        MLRateLimiter rateLimiterWithUnit2 = MLRateLimiter.builder().unit(TimeUnit.NANOSECONDS).build();

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiter, rateLimiterWithNumber2));

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterNull, rateLimiter));

        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithUnit, rateLimiterWithNumber));
        assertTrue(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithNumber, rateLimiterWithUnit));
        assertFalse(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithUnit, rateLimiterWithUnit2));
        assertFalse(MLRateLimiter.isDeployRequiredAfterUpdate(rateLimiterWithNumber, rateLimiterWithNumber2));
    }

    private void testParseFromJsonString(String expectedInputStr, Consumer<MLRateLimiter> verify) throws Exception {
        XContentParser parser = XContentType.JSON
            .xContent()
            .createParser(
                new NamedXContentRegistry(new SearchModule(Settings.EMPTY, Collections.emptyList()).getNamedXContents()),
                LoggingDeprecationHandler.INSTANCE,
                expectedInputStr
            );
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
        assertNull(updatedRateLimiter.getUnit());
        assertNull(updatedRateLimiter.getLimit());
    }
}
