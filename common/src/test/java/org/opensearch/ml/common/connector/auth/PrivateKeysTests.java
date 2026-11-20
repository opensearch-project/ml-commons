/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.ecPkcs8;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.pem;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.rsaPkcs8;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.rsaPkcs8Pem;

import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/** Reading the private key a {@code jwt.private_key} reference names ({@link PrivateKeys}). */
public class PrivateKeysTests {

    private static RSAPrivateKey parsed(String pem) {
        RSAPrivateKey key = PrivateKeys.parsePkcs8(pem);
        assertEquals("RSA", key.getAlgorithm());
        assertArrayEquals(rsaPkcs8(), key.getEncoded());
        return key;
    }

    @Test
    public void parsePkcs8_readsAnUnencryptedPkcs8RsaKeyWhateverItsLineBreaks() {
        parsed(rsaPkcs8Pem());
        parsed(pem("PRIVATE KEY", rsaPkcs8(), false));
        parsed(rsaPkcs8Pem().replace("\n", "\r\n"));
        parsed("\n  " + rsaPkcs8Pem().strip() + "  \n");
    }

    @Test
    public void parsePkcs8_refusesAnRsaKeyShorterThan2048Bits() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> PrivateKeys.parsePkcs8(pem("PRIVATE KEY", rsaPkcs8(1024), true))
        );
        assertEquals(PrivateKeys.KEY_TOO_SHORT_MESSAGE, e.getMessage());
        assertNull(e.getCause());
    }

    @Test
    public void parsePkcs8_refusesEveryOtherFormWithoutEchoingTheInput() {
        String body = Base64.getEncoder().encodeToString(rsaPkcs8());
        Map<String, String> refused = new LinkedHashMap<>();
        refused.put("PKCS#1", pem("RSA PRIVATE KEY", rsaPkcs8(), true));
        refused.put("encrypted PKCS#8", pem("ENCRYPTED PRIVATE KEY", rsaPkcs8(), true));
        refused.put("EC key", pem("PRIVATE KEY", ecPkcs8(), true));
        refused.put("not base64", "-----BEGIN PRIVATE KEY-----\nnot base64!\n-----END PRIVATE KEY-----");
        refused.put("not PKCS#8", pem("PRIVATE KEY", new byte[] { 1, 2, 3 }, false));
        refused.put("no footer", "-----BEGIN PRIVATE KEY-----\n" + body);
        refused.put("text before the header", "Bag Attributes\n" + rsaPkcs8Pem());
        refused.put("overlapping armour", "-----BEGIN PRIVATE KEY-----END PRIVATE KEY-----");
        refused.put("garbage", "secret-value-never-echoed");
        refused.put("empty", "");
        refused.put("null", null);
        for (Map.Entry<String, String> form : refused.entrySet()) {
            IllegalArgumentException e = assertThrows(
                form.getKey(),
                IllegalArgumentException.class,
                () -> PrivateKeys.parsePkcs8(form.getValue())
            );
            assertEquals(form.getKey(), PrivateKeys.INVALID_MESSAGE, e.getMessage());
            assertNull("the message is the whole report; no cause carries the input", e.getCause());
            assertFalse(e.getMessage().contains(body.substring(0, 16)));
            assertFalse(e.getMessage().contains("secret-value"));
        }
    }
}
