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

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

/** Reading the private key a {@code jwt.private_key} reference names ({@link PrivateKeys}). */
public class PrivateKeysTests {

    private static void parsed(String pem) {
        PrivateKey key = PrivateKeys.parsePkcs8(pem, JwtSpec.RS256);
        assertEquals("RSA", key.getAlgorithm());
        assertArrayEquals(rsaPkcs8(), key.getEncoded());
    }

    /** The refusal of {@code pem} for {@code signingAlg}, which carries no cause. */
    private static String refusal(String pem, String signingAlg) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PrivateKeys.parsePkcs8(pem, signingAlg));
        assertNull("the message is the whole report; no cause carries the input", e.getCause());
        return e.getMessage();
    }

    @Test
    public void parsePkcs8_readsAnUnencryptedPkcs8RsaKeyWhateverItsLineBreaks() {
        parsed(rsaPkcs8Pem());
        parsed(pem("PRIVATE KEY", rsaPkcs8(), false));
        parsed(rsaPkcs8Pem().replace("\n", "\r\n"));
        parsed("\n  " + rsaPkcs8Pem().strip() + "  \n");
    }

    @Test
    public void parsePkcs8_readsTheKeyFamilyOfTheAlgorithm() {
        assertArrayEquals(rsaPkcs8(), PrivateKeys.parsePkcs8(rsaPkcs8Pem(), JwtSpec.PS256).getEncoded());
        PrivateKey ec = PrivateKeys.parsePkcs8(pem("PRIVATE KEY", ecPkcs8(), true), JwtSpec.ES256);
        assertEquals("EC", ec.getAlgorithm());
        assertArrayEquals(ecPkcs8(), ec.getEncoded());
    }

    @Test
    public void parsePkcs8_refusesAKeyOfTheOtherFamily() {
        String ecPem = pem("PRIVATE KEY", ecPkcs8(), true);
        assertEquals(PrivateKeys.keyTypeMessage(JwtSpec.RS256), refusal(ecPem, JwtSpec.RS256));
        assertEquals(PrivateKeys.keyTypeMessage(JwtSpec.PS256), refusal(ecPem, JwtSpec.PS256));
        assertEquals(PrivateKeys.keyTypeMessage(JwtSpec.ES256), refusal(rsaPkcs8Pem(), JwtSpec.ES256));
        assertEquals("auth_config.jwt.private_key must be an RSA key for signing_alg [PS256]", PrivateKeys.keyTypeMessage("PS256"));
        assertEquals("auth_config.jwt.private_key must be an EC P-256 key for signing_alg [ES256]", PrivateKeys.keyTypeMessage("ES256"));
    }

    @Test
    public void parsePkcs8_refusesAnRsaKeyShorterThan2048Bits_andAnEcKeyOnAnotherCurve() {
        assertEquals(PrivateKeys.KEY_TOO_SHORT_MESSAGE, refusal(pem("PRIVATE KEY", rsaPkcs8(1024), true), JwtSpec.RS256));
        assertEquals(PrivateKeys.EC_P256_REQUIRED_MESSAGE, refusal(pem("PRIVATE KEY", ecPkcs8(384), true), JwtSpec.ES256));
    }

    @Test
    public void parsePkcs8_refusesAKeyMarkedForRsassaPssOnly() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSASSA-PSS");
        generator.initialize(2048);
        String pssPem = pem("PRIVATE KEY", generator.generateKeyPair().getPrivate().getEncoded(), true);
        for (String signingAlg : JwtSpec.SIGNING_ALGS) {
            assertEquals(signingAlg, PrivateKeys.INVALID_MESSAGE, refusal(pssPem, signingAlg));
        }
    }

    @Test
    public void parsePkcs8_refusesEveryOtherFormWithoutEchoingTheInput() {
        String body = Base64.getEncoder().encodeToString(rsaPkcs8());
        Map<String, String> refused = new LinkedHashMap<>();
        refused.put("PKCS#1", pem("RSA PRIVATE KEY", rsaPkcs8(), true));
        refused.put("encrypted PKCS#8", pem("ENCRYPTED PRIVATE KEY", rsaPkcs8(), true));
        refused.put("not base64", "-----BEGIN PRIVATE KEY-----\nnot base64!\n-----END PRIVATE KEY-----");
        refused.put("not PKCS#8", pem("PRIVATE KEY", new byte[] { 1, 2, 3 }, false));
        refused.put("no footer", "-----BEGIN PRIVATE KEY-----\n" + body);
        refused.put("text before the header", "Bag Attributes\n" + rsaPkcs8Pem());
        refused.put("overlapping armour", "-----BEGIN PRIVATE KEY-----END PRIVATE KEY-----");
        refused.put("garbage", "secret-value-never-echoed");
        refused.put("empty", "");
        refused.put("null", null);
        for (Map.Entry<String, String> form : refused.entrySet()) {
            for (String signingAlg : JwtSpec.SIGNING_ALGS) {
                String message = refusal(form.getValue(), signingAlg);
                assertEquals(form.getKey() + " " + signingAlg, PrivateKeys.INVALID_MESSAGE, message);
                assertFalse(message.contains(body.substring(0, 16)));
                assertFalse(message.contains("secret-value"));
            }
        }
    }
}
