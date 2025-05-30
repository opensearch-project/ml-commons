/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;

import org.junit.Test;
import org.opensearch.core.common.ParsingException;
import org.opensearch.core.xcontent.XContentLocation;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParser.Token;

public class ParseUtilsTests {

    @Test
    public void testToInstant_WithValidTimestamp() throws IOException {
        XContentParser parser = mock(XContentParser.class);
        long timestamp = 1646092800000L;
        when(parser.currentToken()).thenReturn(Token.VALUE_NUMBER);
        when(parser.longValue()).thenReturn(timestamp);

        Instant result = ParseUtils.toInstant(parser);
        assertEquals(Instant.ofEpochMilli(timestamp), result);
    }

    @Test
    public void testToInstant_WithNullValue() throws IOException {
        XContentParser parser = mock(XContentParser.class);
        when(parser.currentToken()).thenReturn(Token.VALUE_NULL);

        Instant result = ParseUtils.toInstant(parser);
        assertNull(result);
    }

    @Test(expected = ParsingException.class)
    public void testToInstant_WithInvalidToken() throws IOException {
        XContentParser parser = mock(XContentParser.class);
        when(parser.currentToken()).thenReturn(Token.START_OBJECT);
        when(parser.getTokenLocation()).thenReturn(new XContentLocation(1, 1));

        ParseUtils.toInstant(parser);
    }
}
