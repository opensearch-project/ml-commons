/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.opensearch.ml.common.connector.auth.JwtSpec;
import org.opensearch.ml.common.connector.auth.PrivateKeys;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * Builds and signs the assertion of the JWT bearer grant (RFC 7523) from the {@code auth_config.jwt} block of a
 * connector: a compact JWS (RFC 7515) whose header is {@link #HEADER}, whose claims are {@code iss}, {@code sub} when
 * configured, {@code aud}, {@code iat}, {@code exp}, {@code jti} and then the configured additional claims, and whose
 * signature is RS256 under the PKCS#8 key the connector credential holds. The key is parsed for one signature and
 * dropped: nothing here keeps, logs or echoes it, and a key that cannot be used is reported with the messages of
 * {@link PrivateKeys} alone, {@link PrivateKeys#INVALID_MESSAGE} or {@link PrivateKeys#KEY_TOO_SHORT_MESSAGE}.
 */
final class JwtAssertionSigner {
    /** RS256 is the only algorithm {@link JwtSpec} admits. */
    static final String HEADER = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    static final String ISSUER_CLAIM = "iss";
    static final String SUBJECT_CLAIM = "sub";
    static final String AUDIENCE_CLAIM = "aud";
    static final String ISSUED_AT_CLAIM = "iat";
    static final String EXPIRATION_CLAIM = "exp";
    static final String JWT_ID_CLAIM = "jti";
    /** The random bytes of a {@code jti}; 22 characters once encoded. */
    static final int JWT_ID_BYTES = 16;

    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final SecureRandom RANDOM = new SecureRandom();

    private JwtAssertionSigner() {}

    /**
     * The signed assertion of {@code jwt}, issued at {@code now} and valid for its lifetime, with the references of
     * its issuer, subject, audience and private key resolved from {@code credentials}.
     *
     * @param effectiveTokenUrl the canonical URL the assertion is sent to; its {@code aud} when the block names none
     * @throws IllegalArgumentException with {@link PrivateKeys#INVALID_MESSAGE} when the key is not an unencrypted
     *     PKCS#8 RSA key the signer can use, with {@link PrivateKeys#KEY_TOO_SHORT_MESSAGE} when it is one of fewer
     *     than 2048 bits
     */
    static String assertion(JwtSpec jwt, CredentialSnapshot credentials, String effectiveTokenUrl, Instant now) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put(ISSUER_CLAIM, credentials.resolve(jwt.getIssuer()));
        if (jwt.getSubject() != null) {
            claims.put(SUBJECT_CLAIM, credentials.resolve(jwt.getSubject()));
        }
        String audience = credentials.resolve(jwt.getAudience());
        claims.put(AUDIENCE_CLAIM, audience != null ? audience : effectiveTokenUrl);
        long issuedAt = now.getEpochSecond();
        claims.put(ISSUED_AT_CLAIM, issuedAt);
        claims.put(EXPIRATION_CLAIM, issuedAt + jwt.getLifetimeSeconds());
        claims.put(JWT_ID_CLAIM, jwtId());
        // Literals that name none of the claims above, in key order.
        claims.putAll(jwt.getAdditionalClaims());
        String signingInput = base64Url(HEADER.getBytes(UTF_8))
            + "."
            + base64Url(StringUtils.MAPPER.writeValueAsString(claims).getBytes(UTF_8));
        byte[] signature = sign(signingInput.getBytes(UTF_8), PrivateKeys.parsePkcs8(credentials.resolve(jwt.getPrivateKey())));
        return signingInput + "." + base64Url(signature);
    }

    /**
     * The RSASSA-PKCS1-v1_5 SHA-256 signature of {@code signingInput} under {@code key}.
     *
     * @throws IllegalArgumentException with {@link PrivateKeys#INVALID_MESSAGE} when the key cannot sign, which the
     *     JDK reports only for a key it parsed but cannot use; the cause is dropped, since it could describe the key
     */
    static byte[] sign(byte[] signingInput, RSAPrivateKey key) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
            signature.initSign(key);
            signature.update(signingInput);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException(PrivateKeys.INVALID_MESSAGE);
        }
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
