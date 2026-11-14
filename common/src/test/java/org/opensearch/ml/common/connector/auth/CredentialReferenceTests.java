/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CredentialReferenceTests {

    private static final String[] NOT_REFERENCES = {
        "${credential.}",
        "${credential.a}x",
        "x${credential.a}",
        "$${credential.a}",
        "${parameters.a}",
        "Bearer ${credential.k}",
        "https://${credential.h}/t",
        "${credential.a",
        "${credential.",
        "${credential.a}${credential.b}",
        "${credential.a${credential.b}",
        "${credential.a:-fallback}",
        "${CREDENTIAL.a}",
        "credential.a",
        "",
        null };

    @Test
    public void keyOf_returnsTheKeyOfAWholeValueReference() {
        assertEquals("client_secret", CredentialReference.keyOf("${credential.client_secret}"));
        assertEquals("a", CredentialReference.keyOf("${credential.a}"));
        assertEquals("secretArn.key", CredentialReference.keyOf("${credential.secretArn.key}"));
        assertTrue(CredentialReference.isReference("${credential.client_secret}"));
    }

    @Test
    public void keyOf_rejectsEverythingElse() {
        for (String value : NOT_REFERENCES) {
            assertFalse(String.valueOf(value), CredentialReference.isReference(value));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CredentialReference.keyOf(value));
            assertEquals("Expected a whole-value ${credential.<key>} reference", e.getMessage());
        }
    }
}
