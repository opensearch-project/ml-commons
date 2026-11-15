/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Reads the private key an {@code auth_config.jwt.private_key} reference names from the connector credential: one
 * unencrypted PKCS#8 PEM document holding an RSA key of at least {@link #MIN_RSA_BITS} bits, the form
 * {@code openssl genpkey} and the cloud consoles emit. The JDK does the parsing; no library is involved.
 */
public final class PrivateKeys {
    public static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    /** The shortest RSA key RS256 admits (RFC 7518 section 3.3). */
    public static final int MIN_RSA_BITS = 2048;
    public static final String INVALID_MESSAGE = "auth_config.jwt.private_key must reference an unencrypted PKCS#8 PEM key ("
        + PEM_HEADER
        + ")";
    public static final String KEY_TOO_SHORT_MESSAGE = "auth_config.jwt.private_key must be an RSA key of at least "
        + MIN_RSA_BITS
        + " bits";

    private PrivateKeys() {}

    /**
     * The RSA key of an unencrypted PKCS#8 PEM document, whatever the line breaks around and inside its base64 body.
     *
     * @throws IllegalArgumentException with {@link #INVALID_MESSAGE} for anything else: a PKCS#1 document
     *     ({@code BEGIN RSA PRIVATE KEY}), an encrypted PKCS#8 document, a body that is not base64 or not a PKCS#8
     *     structure, or a key of another algorithm; with {@link #KEY_TOO_SHORT_MESSAGE} for an RSA key of fewer than
     *     {@link #MIN_RSA_BITS} bits. The message is the whole report: no cause carries the input.
     */
    public static RSAPrivateKey parsePkcs8(String pem) {
        String document = pem == null ? "" : pem.strip();
        if (document.length() < PEM_HEADER.length() + PEM_FOOTER.length()
            || !document.startsWith(PEM_HEADER)
            || !document.endsWith(PEM_FOOTER)) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
        String body = document.substring(PEM_HEADER.length(), document.length() - PEM_FOOTER.length()).replaceAll("\\s", "");
        byte[] encoded = null;
        PrivateKey key = null;
        try {
            encoded = Base64.getDecoder().decode(body);
            key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | InvalidKeySpecException e) {
            // Dropped on purpose: the cause could quote the input.
        } catch (NoSuchAlgorithmException e) {
            // RSA is an algorithm every Java platform provides; treated as the key being unreadable.
        } catch (RuntimeException e) {
            // A provider failing on a structure it did not expect reports in its own words; the same refusal.
        } finally {
            // The key spec and the key object hold their own copies; the decoded bytes are not kept around.
            if (encoded != null) {
                Arrays.fill(encoded, (byte) 0);
            }
        }
        if (!(key instanceof RSAPrivateKey rsa)) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
        if (rsa.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException(KEY_TOO_SHORT_MESSAGE);
        }
        return rsa;
    }
}
