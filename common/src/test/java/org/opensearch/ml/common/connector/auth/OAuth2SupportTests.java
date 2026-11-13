/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.Test;

public class OAuth2SupportTests {

    private static String rejected(String token) {
        return assertThrows(IllegalArgumentException.class, () -> OAuth2Support.sanitizeToken(token)).getMessage();
    }

    @Test
    public void sanitizeToken_acceptsPrintableAsciiUpToTheLimit() {
        for (String token : new String[] { "!", "~", "eyJ0eXAi.eyJzdWIi.c2ln-_~+/=", "a".repeat(OAuth2Support.MAX_TOKEN_LENGTH) }) {
            assertArrayEquals(token.getBytes(StandardCharsets.US_ASCII), OAuth2Support.sanitizeToken(token));
        }
        assertEquals(8172, OAuth2Support.MAX_TOKEN_LENGTH);
    }

    @Test
    public void sanitizeToken_rejectsEmptyLongAndNonPrintableTokens() {
        assertEquals("OAuth2 access token is empty", rejected(null));
        assertEquals(OAuth2Support.TOKEN_EMPTY_MESSAGE, rejected(""));
        assertEquals("OAuth2 access token is longer than 8172 characters", rejected("a".repeat(8173)));
        assertEquals("OAuth2 access token contains a character outside printable ASCII", rejected("a b"));
        for (String token : new String[] { "a\tb", "a\rb", "a\nb", "a\u007Fb", "a\u0080b", "abc\u00e9", "a\u2028b" }) {
            assertEquals(token, OAuth2Support.TOKEN_CHARACTER_MESSAGE, rejected(token));
        }
    }

    @Test
    public void withBearer_addsTheHeaderToACopy() {
        assertEquals(Map.of("Authorization", "Bearer tok-1"), OAuth2Support.withBearer(null, "tok-1"));

        Map<String, String> headers = Map.of("Content-Type", "x");
        Map<String, String> result = OAuth2Support.withBearer(headers, "tok-1");

        assertEquals(Map.of("Content-Type", "x", OAuth2Support.AUTHORIZATION_HEADER, OAuth2Support.BEARER_PREFIX + "tok-1"), result);
        assertEquals(Map.of("Content-Type", "x"), headers);
    }
}
