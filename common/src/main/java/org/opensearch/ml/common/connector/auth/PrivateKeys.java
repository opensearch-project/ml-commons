/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Reads the private key an {@code auth_config.jwt.private_key} reference names from the connector credential: one
 * unencrypted PKCS#8 PEM document holding the key its {@code signing_alg} signs with, an RSA key of at least
 * {@link #MIN_RSA_BITS} bits for RS256 and PS256 or an EC key on the P-256 curve for ES256, the form
 * {@code openssl genpkey} and the cloud consoles emit. The JDK does the parsing; no library is involved.
 */
public final class PrivateKeys {
    public static final String PEM_HEADER = "-----BEGIN PRIVATE KEY-----";
    public static final String PEM_FOOTER = "-----END PRIVATE KEY-----";
    /** The shortest RSA key RS256 and PS256 admit (RFC 7518 sections 3.3 and 3.5). */
    public static final int MIN_RSA_BITS = 2048;
    public static final String INVALID_MESSAGE = "auth_config.jwt.private_key must reference an unencrypted PKCS#8 PEM key ("
        + PEM_HEADER
        + ")";
    public static final String KEY_TOO_SHORT_MESSAGE = "auth_config.jwt.private_key must be an RSA key of at least "
        + MIN_RSA_BITS
        + " bits";
    public static final String EC_P256_REQUIRED_MESSAGE =
        "auth_config.jwt.private_key must be an EC key on the P-256 curve for signing_alg [" + JwtSpec.ES256 + "]";

    private static final String RSA = "RSA";
    private static final String EC = "EC";
    private static final String P256 = "secp256r1";
    private static final int DER_SEQUENCE = 0x30;
    private static final int DER_INTEGER = 0x02;
    /** The object identifier of RSASSA-PSS, 1.2.840.113549.1.1.10, as a DER element. */
    private static final byte[] RSASSA_PSS_OID = { 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0A };

    private PrivateKeys() {}

    /** The message for a key of the other family than {@code signingAlg} signs with: EC for RS256 or PS256, RSA for ES256. */
    public static String keyTypeMessage(String signingAlg) {
        return "auth_config.jwt.private_key must be "
            + (JwtSpec.ES256.equals(signingAlg) ? "an EC P-256 key" : "an RSA key")
            + " for signing_alg ["
            + signingAlg
            + "]";
    }

    /**
     * The key of an unencrypted PKCS#8 PEM document that {@code signingAlg} signs with, whatever the line breaks around
     * and inside its base64 body: an RSA key for RS256 and PS256, an EC key on P-256 for ES256.
     *
     * @throws IllegalArgumentException with {@link #INVALID_MESSAGE} for a document that is not one: a PKCS#1 or SEC1
     *     document ({@code BEGIN RSA PRIVATE KEY}, {@code BEGIN EC PRIVATE KEY}), an encrypted PKCS#8 document, a body
     *     that is not base64 or not a PKCS#8 structure, or a key of an algorithm other than RSA and EC, such as one
     *     marked for RSASSA-PSS only; with {@link #keyTypeMessage} for a key of the other family; with
     *     {@link #KEY_TOO_SHORT_MESSAGE} for an RSA key of fewer than {@link #MIN_RSA_BITS} bits; with
     *     {@link #EC_P256_REQUIRED_MESSAGE} for an EC key on another curve. The message is the whole report: no cause
     *     carries the input.
     */
    public static PrivateKey parsePkcs8(String pem, String signingAlg) {
        String document = pem == null ? "" : pem.strip();
        if (document.length() < PEM_HEADER.length() + PEM_FOOTER.length()
            || !document.startsWith(PEM_HEADER)
            || !document.endsWith(PEM_FOOTER)) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
        String body = document.substring(PEM_HEADER.length(), document.length() - PEM_FOOTER.length()).replaceAll("\\s", "");
        boolean ec = JwtSpec.ES256.equals(signingAlg);
        byte[] encoded;
        try {
            encoded = Base64.getDecoder().decode(body);
        } catch (IllegalArgumentException e) {
            // Dropped on purpose: the cause could quote the input.
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
        PrivateKey key;
        try {
            // Read from the bytes, since the key factories differ: the JDK's refuses such a key, others read it as RSA.
            if (namesRsassaPss(encoded)) {
                throw new IllegalArgumentException(INVALID_MESSAGE);
            }
            key = generate(ec ? EC : RSA, encoded);
            if (key == null && generate(ec ? RSA : EC, encoded) != null) {
                throw new IllegalArgumentException(keyTypeMessage(signingAlg));
            }
        } finally {
            // The key spec and the key object hold their own copies; the decoded bytes are not kept around.
            Arrays.fill(encoded, (byte) 0);
        }
        if (ec) {
            if (!(key instanceof ECPrivateKey ecKey)) {
                throw new IllegalArgumentException(INVALID_MESSAGE);
            }
            if (!isP256(ecKey.getParams())) {
                throw new IllegalArgumentException(EC_P256_REQUIRED_MESSAGE);
            }
            return ecKey;
        }
        if (!(key instanceof RSAPrivateKey rsa)) {
            throw new IllegalArgumentException(INVALID_MESSAGE);
        }
        if (rsa.getModulus().bitLength() < MIN_RSA_BITS) {
            throw new IllegalArgumentException(KEY_TOO_SHORT_MESSAGE);
        }
        return rsa;
    }

    /**
     * Whether the PKCS#8 structure {@code encoded} names RSASSA-PSS as the algorithm of its key: the object identifier
     * that opens the AlgorithmIdentifier SEQUENCE after the version INTEGER of the outer SEQUENCE. False for bytes
     * that are not so structured, which the key factory then refuses.
     */
    private static boolean namesRsassaPss(byte[] encoded) {
        int[] info = element(encoded, 0, DER_SEQUENCE);
        int[] version = info == null ? null : element(encoded, info[0], DER_INTEGER);
        int[] algorithm = version == null ? null : element(encoded, version[0] + version[1], DER_SEQUENCE);
        return algorithm != null
            && algorithm[1] >= RSASSA_PSS_OID.length
            && Arrays.equals(encoded, algorithm[0], algorithm[0] + RSASSA_PSS_OID.length, RSASSA_PSS_OID, 0, RSASSA_PSS_OID.length);
    }

    /**
     * The offset and the length of the content of the DER element at {@code offset} of {@code der}, or null when no
     * element of {@code tag} that ends within {@code der} starts there.
     */
    private static int[] element(byte[] der, int offset, int tag) {
        if (offset + 2 > der.length || (der[offset] & 0xFF) != tag) {
            return null;
        }
        int content = offset + 2;
        int length = der[offset + 1] & 0xFF;
        if (length > 0x7F) {
            // The long form: the low bits count the length bytes that follow; three cover any key.
            int count = length & 0x7F;
            if (count < 1 || count > 3 || content + count > der.length) {
                return null;
            }
            length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | (der[content++] & 0xFF);
            }
        }
        return content + length <= der.length ? new int[] { content, length } : null;
    }

    /** The {@code algorithm} key {@code encoded} holds, or null when it holds none the JDK reads as one. */
    private static PrivateKey generate(String algorithm, byte[] encoded) {
        try {
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            // Dropped on purpose: the cause could quote the input. RSA and EC are algorithms every Java platform
            // provides, so a missing one is treated as the key being unreadable.
            return null;
        } catch (RuntimeException e) {
            // A provider failing on a structure it did not expect reports in its own words; the same refusal.
            return null;
        }
    }

    /**
     * Whether {@code params} are those of P-256. {@link ECParameterSpec} has no value equality, so the curve (field,
     * a and b), the generator, the order and the cofactor are compared with the {@code secp256r1} parameters.
     */
    private static boolean isP256(ECParameterSpec params) {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(EC);
            parameters.init(new ECGenParameterSpec(P256));
            ECParameterSpec p256 = parameters.getParameterSpec(ECParameterSpec.class);
            return params != null
                && p256.getCurve().equals(params.getCurve())
                && p256.getGenerator().equals(params.getGenerator())
                && p256.getOrder().equals(params.getOrder())
                && p256.getCofactor() == params.getCofactor();
        } catch (GeneralSecurityException e) {
            return false;
        }
    }
}
