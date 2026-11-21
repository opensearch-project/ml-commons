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
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.ecPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.ecPublicKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.jwtBearerConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKeyCredential;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPublicKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final String X5T_S256 = "Z7E26IvERGSlI9PxYRR6U4XP1YQ7riAsx0JIHMtn8so";
    /** The signing input of the examples of RFC 7515 Appendix A, after its header: BASE64URL(payload). */
    private static final String RFC_7515_PAYLOAD =
        "eyJpc3MiOiJqb2UiLA0KICJleHAiOjEzMDA4MTkzODAsDQogImh0dHA6Ly9leGFtcGxlLmNvbS9pc19yb290Ijp0cnVlfQ";

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
        return verifies(assertion, key, Signature.getInstance("SHA256withRSA"));
    }

    private static boolean verifies(String assertion, PublicKey key, Signature verifier) throws GeneralSecurityException {
        String[] parts = parts(assertion);
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

    private static byte[] concat(byte[]... parts) {
        byte[] all = new byte[Arrays.stream(parts).mapToInt(part -> part.length).sum()];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, all, offset, part.length);
            offset += part.length;
        }
        return all;
    }

    /** The DER element of {@code tag} whose content is {@code parts}, of fewer than 128 bytes. */
    private static byte[] der(int tag, byte[]... parts) {
        byte[] content = concat(parts);
        return concat(new byte[] { (byte) tag, (byte) content.length }, content);
    }

    private static byte[] filled(int length, int value) {
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) value);
        return bytes;
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
        byte[] signingInput = ("eyJhbGciOiJSUzI1NiJ9" + "." + RFC_7515_PAYLOAD).getBytes(US_ASCII);
        byte[] signature = BASE64URL
            .decode(
                "cC4hiUPoj9Eetdgtv3hF80EGrhuB__dzERat0XF9g2VtQgr9PJbu3XOiZj5RZmh7"
                    + "AAuHIm4Bh-0Qc_lF5YKt_O8W2Fp5jujGbds9uJdbF9CUAr7t1dnZcAcQjbKBYNX4"
                    + "BAynRFdiuB--f_nZLgrnbyTyWzO75vRK5h6xBArLIARNPvkSjtQBMHlb1L07Qe7K"
                    + "0GarZRmB_eSN9383LcOLn6_dO--xi12jzDwusC-eOkHWEsqtFZESc6BfI7noOPqv"
                    + "hJ1phCnvWh6IeYI2w9QOYEUipUTI8np6LbgGY9Fs98rqVt5AXLIhWkWywlVmtVrB"
                    + "p0igcN_IoypGlUPQGe77Rw"
            );

        assertArrayEquals(signature, JwtAssertionSigner.sign(signingInput, key, JwtSpec.RS256));
    }

    @Test
    public void sign_es256_writesTheSignatureInTheFormOfRfc7515AppendixA3() throws Exception {
        // The EC key of the example, given there as a JWK. ECDSA is randomized, so the example's signature is checked
        // by verifying it, and one made here by verifying it under the same key and form.
        AlgorithmParameters p256 = AlgorithmParameters.getInstance("EC");
        p256.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec curve = p256.getParameterSpec(ECParameterSpec.class);
        KeyFactory ec = KeyFactory.getInstance("EC");
        ECPoint w = new ECPoint(
            unsigned("f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU"),
            unsigned("x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0")
        );
        PublicKey publicKey = ec.generatePublic(new ECPublicKeySpec(w, curve));
        PrivateKey key = ec.generatePrivate(new ECPrivateKeySpec(unsigned("jpsQnnGQmL-YBIffH1136cspYG6-0iY7X1fCE9-E9LI"), curve));
        String signingInput = "eyJhbGciOiJFUzI1NiJ9" + "." + RFC_7515_PAYLOAD;
        String example = "DtEhU3ljbEg8L38VWAfUAqOyKAM6-Xx-F4GawxaepmXFCgfTjDxw5djxLa8ISlSApmWQxfKTUJqPP3-Kg6NU1Q";
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        assertTrue(verifies(signingInput + "." + example, publicKey, verifier));

        byte[] signature = JwtAssertionSigner.sign(signingInput.getBytes(US_ASCII), key, JwtSpec.ES256);
        assertEquals("R and S, 32 bytes each", 64, signature.length);
        assertTrue(verifies(signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature), publicKey, verifier));
    }

    @Test
    public void es256Signature_writesTheDerIntegersAs32BytesEach_andRefusesWhatIsNotAnEcdsaSignature() {
        byte[] r = filled(32, 0x11);
        byte[] s = filled(32, 0x22);
        assertArrayEquals(concat(r, s), JwtAssertionSigner.es256Signature(der(0x30, der(0x02, r), der(0x02, s))));
        // A value whose first bit is set carries a leading zero in DER, so that it reads as positive.
        byte[] high = filled(32, 0x91);
        assertArrayEquals(concat(high, s), JwtAssertionSigner.es256Signature(der(0x30, der(0x02, new byte[] { 0 }, high), der(0x02, s))));
        // A value of fewer than 32 bytes is left-padded.
        byte[] shortS = filled(31, 0x22);
        assertArrayEquals(
            concat(r, new byte[] { 0 }, shortS),
            JwtAssertionSigner.es256Signature(der(0x30, der(0x02, r), der(0x02, shortS)))
        );

        byte[] valid = der(0x30, der(0x02, r), der(0x02, s));
        List<byte[]> refused = List
            .of(
                new byte[0],
                der(0x31, der(0x02, r), der(0x02, s)),
                Arrays.copyOf(valid, valid.length - 1),
                concat(valid, new byte[] { 0 }),
                der(0x30, der(0x02, r)),
                der(0x30, der(0x02, r), der(0x04, s)),
                der(0x30, der(0x02, r), der(0x02)),
                der(0x30, der(0x02, high), der(0x02, s)),
                der(0x30, der(0x02, filled(33, 0x11)), der(0x02, s))
            );
        for (byte[] input : refused) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> JwtAssertionSigner.es256Signature(input));
            assertEquals(Arrays.toString(input), PrivateKeys.INVALID_MESSAGE, e.getMessage());
        }
    }

    @Test
    public void sign_refusesAnAlgorithmItDoesNotKnow_andReportsAnyFailureOfTheSignatureAsAnUnusableKey() {
        PrivateKey key = PrivateKeys.parsePkcs8(rsaPkcs8Pem(), JwtSpec.RS256);
        IllegalArgumentException unknown = assertThrows(
            IllegalArgumentException.class,
            () -> JwtAssertionSigner.sign(new byte[] { 1 }, key, "HS256")
        );
        assertEquals(PrivateKeys.INVALID_MESSAGE, unknown.getMessage());
        // Here the JDK fails unchecked, as a provider may on any input.
        IllegalArgumentException unchecked = assertThrows(
            IllegalArgumentException.class,
            () -> JwtAssertionSigner.sign(null, key, JwtSpec.RS256)
        );
        assertEquals(PrivateKeys.INVALID_MESSAGE, unchecked.getMessage());
        assertNull(unchecked.getCause());
    }

    @Test
    public void header_writesTheMemberValuesAsJsonStrings() {
        JwtSpec jwt = jwtBearerConfig().getJwt().toBuilder().header(Map.of(JwtSpec.KID, "a\"b\n")).build();
        String header = JwtAssertionSigner.header(jwt);
        assertEquals("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"a\\\"b\\n\"}", header);
        assertEquals(Map.of("alg", "RS256", "typ", "JWT", "kid", "a\"b\n"), StringUtils.MAPPER.readValue(header, Map.class));
    }

    @Test
    public void assertion_isACompactJwsWithTheRs256Header_thatVerifiesUnderThePublicKey() throws Exception {
        String assertion = JwtAssertionSigner.assertion(jwtBearerConfig().getJwt(), privateKey(), PUBLIC_TOKEN_URL, NOW);

        assertTrue(assertion, assertion.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
        assertEquals("the bytes of a block without header members", "{\"alg\":\"RS256\",\"typ\":\"JWT\"}", decoded(parts(assertion)[0]));
        assertTrue(verifies(assertion, rsaPublicKey()));
        // One flipped payload bit, and the signature no longer verifies.
        String[] parts = parts(assertion);
        String tampered = parts[0] + "." + (parts[1].charAt(0) == 'e' ? 'f' : 'e') + parts[1].substring(1) + "." + parts[2];
        assertFalse(verifies(tampered, rsaPublicKey()));
    }

    @Test
    public void assertion_isSignedWithTheBlocksAlgorithm_underAHeaderWithItsMembersInOrder() throws Exception {
        JwtSpec ps256 = jwtBearerConfig("", "'signing_alg':'PS256','header':{'x5t#S256':'" + X5T_S256 + "','kid':'k1'}").getJwt();
        String assertion = JwtAssertionSigner.assertion(ps256, privateKey(), PUBLIC_TOKEN_URL, NOW);
        assertEquals("{\"alg\":\"PS256\",\"typ\":\"JWT\",\"kid\":\"k1\",\"x5t#S256\":\"" + X5T_S256 + "\"}", decoded(parts(assertion)[0]));
        Signature pss = Signature.getInstance("RSASSA-PSS");
        pss.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
        assertTrue(verifies(assertion, rsaPublicKey(), pss));

        JwtSpec es256 = jwtBearerConfig("", "'signing_alg':'ES256'").getJwt();
        String ecAssertion = JwtAssertionSigner.assertion(es256, snapshot(privateKeyCredential(ecPkcs8Pem())), PUBLIC_TOKEN_URL, NOW);
        assertEquals("{\"alg\":\"ES256\",\"typ\":\"JWT\"}", decoded(parts(ecAssertion)[0]));
        assertTrue(verifies(ecAssertion, ecPublicKey(), Signature.getInstance("SHA256withECDSAinP1363Format")));
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
