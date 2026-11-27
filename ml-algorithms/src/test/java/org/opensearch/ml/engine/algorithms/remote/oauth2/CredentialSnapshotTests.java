/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class CredentialSnapshotTests {

    private static String fingerprint(Map<String, String> credential, String... keys) {
        return CredentialSnapshot.of(credential, Set.of(keys)).fingerprint();
    }

    @Test
    public void of_copiesOnlyTheReferencedKeys() {
        CredentialSnapshot snapshot = CredentialSnapshot.of(Map.of("a", "1", "b", "2"), Set.of("a"));

        assertEquals("1", snapshot.resolve("${credential.a}"));
        assertNull(snapshot.resolve("${credential.b}"));
    }

    @Test
    public void fingerprint_followsTheReferencedValuesOnly() {
        String fingerprint = fingerprint(Map.of("a", "1", "b", "2"), "a");

        assertEquals(fingerprint, fingerprint(Map.of("a", "1", "b", "2"), "a"));
        assertEquals(fingerprint, fingerprint(Map.of("a", "1", "b", "3"), "a"));
        assertNotEquals(fingerprint, fingerprint(Map.of("a", "9", "b", "2"), "a"));
        assertNotEquals(fingerprint, fingerprint(Map.of("a", "1", "b", "2"), "a", "b"));
        // A key and its value cannot run into each other.
        assertNotEquals(fingerprint(Map.of("ab", "c"), "ab"), fingerprint(Map.of("a", "bc"), "a"));
    }

    @Test
    public void resolve_replacesOnlyAWholeReference() {
        CredentialSnapshot snapshot = CredentialSnapshot.of(Map.of("a", "1"), Set.of("a"));

        assertNull(snapshot.resolve(null));
        assertEquals("https://idp.example/token", snapshot.resolve("https://idp.example/token"));
        assertEquals("1", snapshot.resolve("${credential.a}"));
        assertEquals("x${credential.a}", snapshot.resolve("x${credential.a}"));
    }
}
