/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.conversation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.util.Base64;

import org.junit.Test;

public class PaginationTokenUtilTests {

    @Test
    public void testEncodeDecode_roundTrip() {
        for (int offset : new int[] { 0, 1, 2, 6, 7, 10, 999, Integer.MAX_VALUE }) {
            String token = PaginationTokenUtil.encodeOffset(offset);
            assertEquals(offset, PaginationTokenUtil.decodeOffset(token));
        }
    }

    @Test
    public void testEncode_knownValues() {
        // Base64url of "2" is "Mg", of "6" is "Ng"
        assertEquals("Mg", PaginationTokenUtil.encodeOffset(2));
        assertEquals("Ng", PaginationTokenUtil.encodeOffset(6));
    }

    @Test
    public void testEncode_negativeOffset_returnsNull() {
        assertNull(PaginationTokenUtil.encodeOffset(-1));
    }

    @Test
    public void testDecode_isOpaque_notPlainInteger() {
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset("2"));
    }

    @Test
    public void testDecode_null_thenFail() {
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset(null));
    }

    @Test
    public void testDecode_empty_thenFail() {
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset(""));
    }

    @Test
    public void testDecode_malformedBase64_thenFail() {
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset("!!!not-base64!!!"));
    }

    @Test
    public void testDecode_nonNumericPayload_thenFail() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString("abc".getBytes());
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset(token));
    }

    @Test
    public void testDecode_negativePayload_thenFail() {
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString("-5".getBytes());
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.decodeOffset(token));
    }

    @Test
    public void testResolveOffset_neither_returnsNull() {
        assertNull(PaginationTokenUtil.resolveOffset(null, null));
    }

    @Test
    public void testResolveOffset_legacyOnly() {
        assertEquals(Integer.valueOf(7), PaginationTokenUtil.resolveOffset(null, "7"));
    }

    @Test
    public void testResolveOffset_pageTokenOnly() {
        // "Ng" = offset 6
        assertEquals(Integer.valueOf(6), PaginationTokenUtil.resolveOffset("Ng", null));
    }

    @Test
    public void testResolveOffset_pageTokenTakesPrecedenceOverLegacy() {
        // "Ng" = offset 6, wins over legacy 99
        assertEquals(Integer.valueOf(6), PaginationTokenUtil.resolveOffset("Ng", "99"));
    }

    @Test
    public void testResolveOffset_invalidPageToken_thenFail() {
        // malformed page token fails hard; no fallback to the valid legacy token
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.resolveOffset("!!!not-base64!!!", "99"));
    }

    @Test
    public void testResolveOffset_invalidLegacyToken_thenFail() {
        assertThrows(IllegalArgumentException.class, () -> PaginationTokenUtil.resolveOffset(null, "not-an-int"));
    }
}
