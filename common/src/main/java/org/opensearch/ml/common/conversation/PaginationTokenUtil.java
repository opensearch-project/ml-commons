/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.conversation;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes/decodes the memory pagination offset as an opaque {@code next_page_token} string.
 *
 * <p>Pagination stays a 0-based int offset internally, so the wire/transport format is unchanged and
 * this is backward compatible; the token is only an opaque REST-layer encoding of that offset that
 * clients pass back verbatim rather than parse.
 */
public final class PaginationTokenUtil {

    private PaginationTokenUtil() {}

    /** Returns the opaque token for a non-negative offset, or {@code null} for a negative offset (no next page). */
    public static String encodeOffset(int offset) {
        if (offset < 0) {
            return null;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an opaque token back to its offset; throws {@link IllegalArgumentException} if malformed or negative. */
    public static int decodeOffset(String token) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Invalid " + ActionConstants.NEXT_PAGE_TOKEN_FIELD + ": token must not be empty");
        }
        final int offset;
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            offset = Integer.parseInt(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + ActionConstants.NEXT_PAGE_TOKEN_FIELD + ": " + token, e);
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Invalid " + ActionConstants.NEXT_PAGE_TOKEN_FIELD + ": " + token);
        }
        return offset;
    }

    /**
     * Resolves the offset from the two accepted params: opaque {@code next_page_token} wins over legacy
     * {@code next_token}; returns {@code null} if neither is set. Takes raw strings (not {@code RestRequest})
     * so {@code common} keeps no REST dependency.
     */
    public static Integer resolveOffset(String pageToken, String legacyToken) {
        if (pageToken != null) {
            return decodeOffset(pageToken);
        }
        if (legacyToken != null) {
            return Integer.parseInt(legacyToken);
        }
        return null;
    }
}
