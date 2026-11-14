/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** The access token as it enters a model request: its accepted form and the header that carries it. */
public final class OAuth2Support {
    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    /** "Authorization" (13 characters) + "Bearer " (7) + 8172 = 8192, the per-header limit request headers are checked against. */
    public static final int MAX_TOKEN_LENGTH = 8172;
    public static final String TOKEN_EMPTY_MESSAGE = "OAuth2 access token is empty";
    public static final String TOKEN_TOO_LONG_MESSAGE = "OAuth2 access token is longer than " + MAX_TOKEN_LENGTH + " characters";
    public static final String TOKEN_CHARACTER_MESSAGE = "OAuth2 access token contains a character outside printable ASCII";

    private OAuth2Support() {}

    /**
     * The token as US-ASCII bytes when it is non-empty, at most {@link #MAX_TOKEN_LENGTH} long and made only of 0x21-0x7E.
     *
     * @throws IllegalArgumentException otherwise; the message never repeats the token
     */
    public static byte[] sanitizeToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException(TOKEN_EMPTY_MESSAGE);
        }
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException(TOKEN_TOO_LONG_MESSAGE);
        }
        // Each char is checked before the conversion, which would turn a non-ASCII char into '?'.
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c < 0x21 || c > 0x7E) {
                throw new IllegalArgumentException(TOKEN_CHARACTER_MESSAGE);
            }
        }
        return token.getBytes(StandardCharsets.US_ASCII);
    }

    /** A new map: {@code headers} (may be null or immutable, never modified) plus {@code Authorization: Bearer token}. */
    public static Map<String, String> withBearer(Map<String, String> headers, String token) {
        Map<String, String> result = headers == null ? new HashMap<>() : new HashMap<>(headers);
        result.put(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
        return result;
    }
}
