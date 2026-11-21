/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opensearch.ml.common.connector.auth.JwtSpec;
import org.opensearch.ml.common.connector.auth.PrivateKeys;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * Builds and signs, from the {@code auth_config.jwt} block of a connector, the assertion of the JWT bearer grant (RFC
 * 7523 section 2.1) or the client assertion of {@code private_key_jwt} (RFC 7523 section 2.2, OpenID Connect Core
 * section 9): a compact JWS (RFC 7515) whose header is {@code alg}, {@code typ} {@code JWT} and then the block's header
 * members, whose claims are {@code iss}, {@code sub}, {@code aud}, {@code iat}, {@code exp}, {@code jti} and then the
 * configured additional claims, and whose signature is RS256, PS256 or ES256 under the PKCS#8 key the connector
 * credential holds. Each call signs a new assertion, with its own {@code jti} and {@code iat}. The key is parsed for one
 * signature and dropped: nothing here keeps, logs or echoes it, and a key that cannot be used is reported with the
 * messages of {@link PrivateKeys} alone.
 */
final class JwtAssertionSigner {
    static final String ALGORITHM_HEADER = "alg";
    static final String TYPE_HEADER = "typ";
    static final String JWT_TYPE = "JWT";
    static final String RS256_SIGNATURE = "SHA256withRSA";
    static final String PS256_SIGNATURE = "RSASSA-PSS";
    /**
     * ECDSA with SHA-256, under the name the JDK and FIPS providers alike register. Its signature is DER, which
     * {@link #es256Signature} rewrites as R and S of 32 bytes each, as RFC 7518 section 3.4 requires.
     */
    static final String ES256_SIGNATURE = "SHA256withECDSA";
    /** The length of R and of S in an ES256 signature. */
    static final int ES256_INTEGER_BYTES = 32;
    /**
     * PS256 as RFC 7518 section 3.5 defines it. Always set: a provider has no default it must apply, and some refuse to
     * sign without one.
     */
    static final PSSParameterSpec PS256_PARAMETERS = new PSSParameterSpec(
        "SHA-256",
        "MGF1",
        MGF1ParameterSpec.SHA256,
        32,
        PSSParameterSpec.TRAILER_FIELD_BC
    );
    static final String ISSUER_CLAIM = "iss";
    static final String SUBJECT_CLAIM = "sub";
    static final String AUDIENCE_CLAIM = "aud";
    static final String ISSUED_AT_CLAIM = "iat";
    static final String EXPIRATION_CLAIM = "exp";
    static final String JWT_ID_CLAIM = "jti";
    /** The random bytes of a {@code jti}; 22 characters once encoded. */
    static final int JWT_ID_BYTES = 16;

    private static final byte DER_SEQUENCE = 0x30;
    private static final byte DER_INTEGER = 0x02;
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtAssertionSigner() {}

    /**
     * The signed assertion of the JWT bearer grant for {@code jwt}, issued at {@code now} and valid for its lifetime,
     * with the references of its issuer, subject, audience and private key resolved from {@code credentials}; the
     * subject only when the block names one.
     *
     * @param effectiveTokenUrl the canonical URL the assertion is sent to; its {@code aud} when the block names none
     * @throws IllegalArgumentException with a message of {@link PrivateKeys#parsePkcs8} when the key is not an
     *     unencrypted PKCS#8 key the block's algorithm signs with, with {@link PrivateKeys#INVALID_MESSAGE} when the
     *     JDK cannot sign with it
     */
    static String assertion(JwtSpec jwt, CredentialSnapshot credentials, String effectiveTokenUrl, Instant now) {
        String issuer = credentials.resolve(jwt.getIssuer());
        return signed(jwt, claims(jwt, issuer, credentials.resolve(jwt.getSubject()), credentials, effectiveTokenUrl, now), credentials);
    }

    /**
     * The signed client assertion of {@code private_key_jwt} for {@code jwt}: {@link #assertion} with {@code iss} and
     * {@code sub} both {@code clientId}, as RFC 7523 section 3 and OpenID Connect Core section 9 require.
     *
     * @param clientId the client id, resolved
     * @param effectiveTokenUrl the canonical URL the assertion is sent to; its {@code aud} when the block names none
     * @throws IllegalArgumentException as {@link #assertion} does
     */
    static String clientAssertion(JwtSpec jwt, String clientId, CredentialSnapshot credentials, String effectiveTokenUrl, Instant now) {
        return signed(jwt, claims(jwt, clientId, clientId, credentials, effectiveTokenUrl, now), credentials);
    }

    /** The claims in order: {@code iss}, {@code sub} unless null, {@code aud}, {@code iat}, {@code exp}, {@code jti}, the additional ones. */
    private static Map<String, Object> claims(
        JwtSpec jwt,
        String issuer,
        String subject,
        CredentialSnapshot credentials,
        String effectiveTokenUrl,
        Instant now
    ) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(ISSUER_CLAIM, issuer);
        if (subject != null) {
            claims.put(SUBJECT_CLAIM, subject);
        }
        String audience = credentials.resolve(jwt.getAudience());
        claims.put(AUDIENCE_CLAIM, audience != null ? audience : effectiveTokenUrl);
        long issuedAt = now.getEpochSecond();
        claims.put(ISSUED_AT_CLAIM, issuedAt);
        claims.put(EXPIRATION_CLAIM, issuedAt + jwt.getLifetimeSeconds());
        claims.put(JWT_ID_CLAIM, jwtId());
        // Literals that name none of the claims above, in key order.
        claims.putAll(jwt.getAdditionalClaims());
        return claims;
    }

    /** The compact JWS of {@code claims} under the header and the key of {@code jwt}. */
    private static String signed(JwtSpec jwt, Map<String, Object> claims, CredentialSnapshot credentials) {
        String signingInput = base64Url(header(jwt).getBytes(UTF_8))
            + "."
            + base64Url(StringUtils.MAPPER.writeValueAsString(claims).getBytes(UTF_8));
        String signingAlg = jwt.getSigningAlg();
        PrivateKey key = PrivateKeys.parsePkcs8(credentials.resolve(jwt.getPrivateKey()), signingAlg);
        byte[] signature = sign(signingInput.getBytes(UTF_8), key, signingAlg);
        return signingInput + "." + base64Url(signature);
    }

    /**
     * The JWS header of {@code jwt}: {@code alg}, {@code typ} and then the block's header members in key order, written
     * as the claims are. For RS256 without members, {@code {"alg":"RS256","typ":"JWT"}}.
     */
    static String header(JwtSpec jwt) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put(ALGORITHM_HEADER, jwt.getSigningAlg());
        header.put(TYPE_HEADER, JWT_TYPE);
        header.putAll(jwt.getHeader());
        return StringUtils.MAPPER.writeValueAsString(header);
    }

    /**
     * The {@code signingAlg} signature of {@code signingInput} under {@code key}: RSASSA-PKCS1-v1_5 SHA-256 for RS256,
     * RSASSA-PSS with {@link #PS256_PARAMETERS} for PS256, ECDSA SHA-256 in its 64-byte form for ES256.
     *
     * @throws IllegalArgumentException with {@link PrivateKeys#INVALID_MESSAGE} for any other algorithm, and when the
     *     key cannot sign, which the JDK reports only for a key it parsed but cannot use; the cause is dropped, since
     *     it could describe the key
     */
    static byte[] sign(byte[] signingInput, PrivateKey key, String signingAlg) {
        try {
            Signature signature;
            switch (signingAlg) {
                case JwtSpec.RS256 -> signature = Signature.getInstance(RS256_SIGNATURE);
                case JwtSpec.PS256 -> {
                    signature = Signature.getInstance(PS256_SIGNATURE);
                    signature.setParameter(PS256_PARAMETERS);
                }
                case JwtSpec.ES256 -> signature = Signature.getInstance(ES256_SIGNATURE);
                default -> throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
            }
            signature.initSign(key);
            signature.update(signingInput);
            byte[] value = signature.sign();
            return JwtSpec.ES256.equals(signingAlg) ? es256Signature(value) : value;
        } catch (GeneralSecurityException | RuntimeException e) {
            // A provider may also fail unchecked, such as with a ProviderException; every failure reads the same.
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
    }

    /**
     * The ES256 form of the DER {@code ECDSA-Sig-Value} {@code der}, a SEQUENCE of the INTEGERs R and S: R and then S,
     * each unsigned, big-endian and left-padded to {@link #ES256_INTEGER_BYTES} bytes (RFC 7518 section 3.4).
     *
     * @throws IllegalArgumentException with {@link PrivateKeys#INVALID_MESSAGE} when {@code der} is not such a
     *     SEQUENCE, or R or S is negative or longer than {@link #ES256_INTEGER_BYTES} bytes without its leading zeros
     */
    static byte[] es256Signature(byte[] der) {
        // A P-256 signature is at most 72 bytes, so every length is in the short form.
        if (der.length < 2 || der[0] != DER_SEQUENCE || der[1] != der.length - 2) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
        byte[] signature = new byte[2 * ES256_INTEGER_BYTES];
        int end = copyInteger(der, copyInteger(der, 2, signature, 0), signature, ES256_INTEGER_BYTES);
        if (end != der.length) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
        return signature;
    }

    /**
     * Copies the value of the DER INTEGER at {@code offset} of {@code der}, without its leading zeros, right-aligned
     * into the {@link #ES256_INTEGER_BYTES} bytes at {@code target} of {@code signature}.
     *
     * @return the offset after the INTEGER
     */
    private static int copyInteger(byte[] der, int offset, byte[] signature, int target) {
        if (offset + 2 > der.length || der[offset] != DER_INTEGER) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
        int start = offset + 2;
        int end = start + der[offset + 1];
        if (der[offset + 1] <= 0 || end > der.length || der[start] < 0) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
        while (start < end && der[start] == 0) {
            start++;
        }
        int length = end - start;
        if (length > ES256_INTEGER_BYTES) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
        System.arraycopy(der, start, signature, target + ES256_INTEGER_BYTES - length, length);
        return end;
    }

    private static String jwtId() {
        byte[] bytes = new byte[JWT_ID_BYTES];
        RANDOM.nextBytes(bytes);
        return base64Url(bytes);
    }

    private static String base64Url(byte[] bytes) {
        return BASE64URL.encodeToString(bytes);
    }
}
