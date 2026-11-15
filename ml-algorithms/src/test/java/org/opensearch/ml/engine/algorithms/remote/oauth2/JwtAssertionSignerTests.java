/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.ISSUER;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PKCS1_PEM;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.jwtBearerConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKeyCredential;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPublicKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.ml.common.connector.auth.JwtSpec;
import org.opensearch.ml.common.connector.auth.PrivateKeys;
import org.opensearch.ml.common.utils.StringUtils;

public class JwtAssertionSignerTests {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final Base64.Decoder BASE64URL = Base64.getUrlDecoder();

    /** The three parts of a compact JWS. */
    private static String[] parts(String assertion) {
        String[] parts = assertion.split("\\.", -1);
        assertEquals(assertion, 3, parts.length);
        return parts;
    }

    private static String decoded(String base64Url) {
        return new String(BASE64URL.decode(base64Url), UTF_8);
    }

    /** The claims of {@code assertion}, in the order they were written. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> claims(String assertion) {
        return StringUtils.MAPPER.readValue(decoded(parts(assertion)[1]), LinkedHashMap.class);
    }

    private static boolean verifies(String assertion, PublicKey key) throws GeneralSecurityException {
        String[] parts = parts(assertion);
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(UTF_8));
        return verifier.verify(BASE64URL.decode(parts[2]));
    }

    private static long number(Map<String, Object> claims, String name) {
        return ((Number) claims.get(name)).longValue();
    }

    /** A JWK parameter, base64url of a big-endian unsigned integer. */
    private static BigInteger unsigned(String base64Url) {
        return new BigInteger(1, BASE64URL.decode(base64Url));
    }

    @Test
    public void sign_producesTheSignatureOfRfc7515AppendixA2() throws Exception {
        // The RSA key of the example, given there as a JWK.
        RSAPrivateCrtKeySpec spec = new RSAPrivateCrtKeySpec(
            unsigned(
                "ofgWCuLjybRlzo0tZWJjNiuSfb4p4fAkd_wWJcyQoTbji9k0l8W26mPddxHmfHQp-Vaw-4qPCJrcS2mJPMEzP1Pt0Bm4d4QlL-yRT-SFd2lZS-pCgNMs"
                    + "D1W_YpRPEwOWvG6b32690r2jZ47soMZo9wGzjb_7OMg0LOL-bSf63kpaSHSXndS5z5rexMdbBYUsLA9e-KXBdQOS-UTo7WTBEMa2R2CapHg665xsmtdV"
                    + "MTBQY4uDZlxvb3qCo5ZwKh9kG4LT6_I5IhlJH7aGhyxXFvUK-DWNmoudF8NAco9_h9iaGNj8q2ethFkMLs91kzk2PAcDTW9gb54h4FRWyuXpoQ"
            ),
            unsigned("AQAB"),
            unsigned(
                "Eq5xpGnNCivDflJsRQBXHx1hdR1k6Ulwe2JZD50LpXyWPEAeP88vLNO97IjlA7_GQ5sLKMgvfTeXZx9SE-7YwVol2NXOoAJe46sui395IW_GO-pWJ1O0"
                    + "BkTGoVEn2bKVRUCgu-GjBVaYLU6f3l9kJfFNS3E0QbVdxzubSu3Mkqzjkn439X0M_V51gfpRLI9JYanrC4D4qAdGcopV_0ZHHzQlBjudU2QvXt4ehNYT"
                    + "CBr6XCLQUShb1juUO1ZdiYoFaFQT5Tw8bGUl_x_jTj3ccPDVZFD9pIuhLhBOneufuBiB4cS98l2SR_RQyGWSeWjnczT0QU91p1DhOVRuOopznQ"
            ),
            unsigned(
                "4BzEEOtIpmVdVEZNCqS7baC4crd0pqnRH_5IB3jw3bcxGn6QLvnEtfdUdiYrqBdss1l58BQ3KhooKeQTa9AB0Hw_Py5PJdTJNPY8cQn7ouZ2KKDcmnPG"
                    + "BY5t7yLc1QlQ5xHdwW1VhvKn-nXqhJTBgIPgtldC-KDV5z-y2XDwGUc"
            ),
            unsigned(
                "uQPEfgmVtjL0Uyyx88GZFF1fOunH3-7cepKmtH4pxhtCoHqpWmT8YAmZxaewHgHAjLYsp1ZSe7zFYHj7C6ul7TjeLQeZD_YwD66t62wDmpe_HlB-TnBA"
                    + "-njbglfIsRLtXlnDzQkv5dTltRJ11BKBBypeeF6689rjcJIDEz9RWdc"
            ),
            unsigned(
                "BwKfV3Akq5_MFZDFZCnW-wzl-CCo83WoZvnLQwCTeDv8uzluRSnm71I3QCLdhrqE2e9YkxvuxdBfpT_PI7Yz-FOKnu1R6HsJeDCjn12Sk3vmAktV2zb"
                    + "34MCdy7cpdTh_YVr7tss2u6vneTwrA86rZtu5Mbr1C1XsmvkxHQAdYo0"
            ),
            unsigned(
                "h_96-mK1R_7glhsum81dZxjTnYynPbZpHziZjeeHcXYsXaaMwkOlODsWa7I9xXDoRwbKgB719rrmI2oKr6N3Do9U0ajaHF-NKJnwgjMd2w9cjz3_-ky"
                    + "NlxAr2v4IKhGNpmM5iIgOS1VZnOZ68m6_pbLBSp3nssTdlqvd0tIiTHU"
            ),
            unsigned(
                "IYd7DHOhrWvxkwPQsRM2tOgrjbcrfvtQJipd-DlcxyVuuM9sQLdgjVk2oy26F0EmpScGLq2MowX7fhd_QJQ3ydy5cY7YIBi87w93IKLEdfnbJtoOPLU"
                    + "W0ITrJReOgo1cq9SbsxYawBgfp_gh6A5603k2-ZQwVK0JKSHuLFkuQ3U"
            )
        );
        RSAPrivateKey key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        // The signing input of the example: BASE64URL(header) '.' BASE64URL(payload), as ASCII.
        byte[] signingInput = ("eyJhbGciOiJSUzI1NiJ9"
            + "."
            + "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ").getBytes(US_ASCII);
        byte[] signature = BASE64URL
            .decode(
                "cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7"
                    + "AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1dnZcAcQjbKBYNX4"
                    + "BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe7K"
                    + "0GarZRmB_eSN9383LcOLn6_dO--xi12jzDwusC-eOkHWEsqtFZESc6BfI7noOPqv"
                    + "hJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVmtVrB"
                    + "p0igcN_IoypGlUPQGe77Rw"
            );

        assertArrayEquals(signature, JwtAssertionSigner.sign(signingInput, key));
    }

    @Test
    public void assertion_isACompactJwsWithTheRs256Header_thatVerifiesUnderThePublicKey() throws Exception {
        String assertion = JwtAssertionSigner.assertion(jwtBearerConfig().getJwt(), privateKey(), PUBLIC_TOKEN_URL, NOW);

        assertTrue(assertion, assertion.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
        assertEquals(Map.of("alg", "RS256", "typ", "JWT"), StringUtils.MAPPER.readValue(decoded(parts(assertion)[0]), Map.class));
        assertTrue(verifies(assertion, rsaPublicKey()));
        // One flipped payload bit, and the signature no longer verifies.
        String[] parts = parts(assertion);
        String tampered = parts[0] + "." + (parts[1].charAt(0) == 'e' ? 'f' : 'e') + parts[1].substring(1) + "." + parts[2];
        assertFalse(verifies(tampered, rsaPublicKey()));
    }

    @Test
    public void assertion_claimsAreInOrder_withTheSubjectAndTheAudienceOnlyAsConfigured() {
        Map<String, Object> smallest = claims(
            JwtAssertionSigner.assertion(jwtBearerConfig().getJwt(), privateKey(), PUBLIC_TOKEN_URL, NOW)
        );
        assertEquals(List.of("iss", "aud", "iat", "exp", "jti"), new ArrayList<>(smallest.keySet()));
        assertEquals(ISSUER, smallest.get("iss"));
        assertEquals("the token endpoint, when the block names no audience", PUBLIC_TOKEN_URL, smallest.get("aud"));
        assertEquals(NOW.getEpochSecond(), number(smallest, "iat"));
        assertEquals(NOW.getEpochSecond() + JwtSpec.DEFAULT_LIFETIME_SECONDS, number(smallest, "exp"));

        JwtSpec full = jwtBearerConfig(
            "",
            "'subject':'user@example.com','audience':'https://api.example','lifetime_seconds':120,"
                + "'additional_claims':{'tenant':'t1','scope':'https://www.googleapis.com/auth/cloud-platform'}"
        ).getJwt();
        Map<String, Object> claims = claims(JwtAssertionSigner.assertion(full, privateKey(), PUBLIC_TOKEN_URL, NOW));
        assertEquals(List.of("iss", "sub", "aud", "iat", "exp", "jti", "scope", "tenant"), new ArrayList<>(claims.keySet()));
        assertEquals(ISSUER, claims.get("iss"));
        assertEquals("user@example.com", claims.get("sub"));
        assertEquals("https://api.example", claims.get("aud"));
        assertEquals(NOW.getEpochSecond(), number(claims, "iat"));
        assertEquals(NOW.getEpochSecond() + 120, number(claims, "exp"));
        assertEquals("https://www.googleapis.com/auth/cloud-platform", claims.get("scope"));
        assertEquals("t1", claims.get("tenant"));
    }

    @Test
    public void assertion_jtiIsSixteenRandomBytes() {
        JwtSpec jwt = jwtBearerConfig().getJwt();

        String first = (String) claims(JwtAssertionSigner.assertion(jwt, privateKey(), PUBLIC_TOKEN_URL, NOW)).get("jti");
        String second = (String) claims(JwtAssertionSigner.assertion(jwt, privateKey(), PUBLIC_TOKEN_URL, NOW)).get("jti");

        assertTrue(first, first.matches("[A-Za-z0-9_-]{22}"));
        assertEquals(16, BASE64URL.decode(first).length);
        assertNotEquals(first, second);
    }

    @Test
    public void assertion_resolvesTheReferencesOfTheIssuerSubjectAndAudience() {
        JwtSpec jwt = jwtBearerConfig("", "'subject':'${credential.sub}','audience':'${credential.aud}'")
            .getJwt()
            .toBuilder()
            .issuer("${credential.client_email}")
            .build();
        Map<String, String> credential = new HashMap<>(privateKeyCredential(rsaPkcs8Pem()));
        credential.put("client_email", "other@project.iam.gserviceaccount.com");
        credential.put("sub", "user@example.com");
        credential.put("aud", "https://api.example");

        Map<String, Object> claims = claims(JwtAssertionSigner.assertion(jwt, snapshot(credential), PUBLIC_TOKEN_URL, NOW));

        assertEquals("other@project.iam.gserviceaccount.com", claims.get("iss"));
        assertEquals("user@example.com", claims.get("sub"));
        assertEquals("https://api.example", claims.get("aud"));
    }

    @Test
    public void assertion_refusesAKeyThatIsNotAnUnencryptedPkcs8RsaKey_withoutEchoingIt() {
        JwtSpec jwt = jwtBearerConfig().getJwt();
        for (String pem : new String[] { PKCS1_PEM, "-----BEGIN PRIVATE KEY-----\nAQID\n-----END PRIVATE KEY-----", "", "never-echoed" }) {
            IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> JwtAssertionSigner.assertion(jwt, snapshot(privateKeyCredential(pem)), PUBLIC_TOKEN_URL, NOW)
            );
            assertEquals(PrivateKeys.INVALID_MESSAGE, e.getMessage());
            assertNull(e.getCause());
            assertFalse(e.getMessage().contains("never-echoed"));
        }
    }
}
