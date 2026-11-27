/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.FULL_JWT_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.JWT_BEARER_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.JWT_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.document;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullJwt;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.jwt;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.jwtBuilder;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parseMessage;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.roundTrip;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.Test;
import org.opensearch.Version;

/** The value rules, emission and stream form of the {@code auth_config.jwt} block ({@link JwtSpec}). */
public class JwtSpecTests {

    /** The message {@code builder} fails to build with. */
    private static String buildMessage(JwtSpec.JwtSpecBuilder builder) {
        return assertThrows(IllegalArgumentException.class, builder::build).getMessage();
    }

    private static String buildMessage(UnaryOperator<JwtSpec.JwtSpecBuilder> member) {
        return buildMessage(member.apply(jwtBuilder()));
    }

    /** The message {@link OAuth2AuthConfig#parse} refuses a jwt-bearer block with, once its {@code jwt} is {@code jwtJson}. */
    private static String parseJwtMessage(String jwtJson) {
        Map<String, Object> block = document(JWT_BEARER_JSON);
        block.put(OAuth2AuthConfig.JWT_FIELD, document(jwtJson));
        return parseMessage(block);
    }

    @Test
    public void defaults() {
        JwtSpec spec = jwt();
        assertEquals(JwtSpec.DEFAULT_SIGNING_ALG, spec.getSigningAlg());
        assertEquals(JwtSpec.DEFAULT_LIFETIME_SECONDS, spec.getLifetimeSeconds());
        assertNull("never defaulted", spec.getSubject());
        assertNull("the signer addresses the token endpoint", spec.getAudience());
        assertEquals("never null", Map.of(), spec.getAdditionalClaims());
        assertEquals(spec, jwtBuilder().signingAlg(null).lifetimeSeconds(null).additionalClaims(null).build());
    }

    @Test
    public void issuer_isRequiredAndNotEmpty() {
        assertEquals(JwtSpec.ISSUER_REQUIRED_MESSAGE, buildMessage(b -> b.issuer(null)));
        assertEquals("auth_config.jwt.issuer must not be empty", buildMessage(b -> b.issuer("")));
    }

    @Test
    public void issuerSubjectAndAudience_areLiteralsOrWholeValueCredentialReferences() {
        JwtSpec referencing = jwtBuilder().issuer("${credential.i}").subject("${credential.u}").audience("${credential.a}").build();
        assertEquals("${credential.i}", referencing.getIssuer());
        assertEquals("${credential.u}", referencing.getSubject());
        assertEquals("${credential.a}", referencing.getAudience());

        // The message with the value in place of the member.
        Map<String, UnaryOperator<String>> members = new LinkedHashMap<>();
        members.put("issuer", value -> buildMessage(b -> b.issuer(value)));
        members.put("subject", value -> buildMessage(b -> b.subject(value)));
        members.put("audience", value -> buildMessage(b -> b.audience(value)));
        for (Map.Entry<String, UnaryOperator<String>> member : members.entrySet()) {
            String path = "auth_config.jwt." + member.getKey();
            String parameters = member.getValue().apply("${parameters.x}");
            assertEquals(path + " must not use ${parameters.*}: auth_config is read from the stored connector only", parameters);
            assertFalse(parameters, parameters.contains("parameters.x"));
            assertEquals(
                path + " must be a whole-value ${credential.<key>} reference or contain no ${",
                member.getValue().apply("a-${credential.x}")
            );
        }
        assertEquals("auth_config.jwt.subject must not be empty", buildMessage(b -> b.subject("")));
        assertEquals("auth_config.jwt.audience must not be empty", buildMessage(b -> b.audience("")));
    }

    @Test
    public void signingAlg_isRs256() {
        assertEquals("RS256", jwtBuilder().signingAlg("RS256").build().getSigningAlg());
        for (String alg : new String[] { "PS256", "ES256" }) {
            assertEquals(JwtSpec.signingAlgNotSupportedMessage(alg), buildMessage(b -> b.signingAlg(alg)));
        }
        for (String alg : new String[] { "rs256", "HS256", "none", "" }) {
            assertEquals(alg, JwtSpec.SIGNING_ALG_MESSAGE, buildMessage(b -> b.signingAlg(alg)));
        }
    }

    @Test
    public void lifetimeSeconds_isBetweenOneAndAnHour() {
        assertEquals(1, jwtBuilder().lifetimeSeconds(1).build().getLifetimeSeconds());
        assertEquals(3600, jwtBuilder().lifetimeSeconds(3600).build().getLifetimeSeconds());
        for (int lifetime : new int[] { 0, -1, 3601 }) {
            assertEquals(JwtSpec.LIFETIME_MESSAGE, buildMessage(b -> b.lifetimeSeconds(lifetime)));
        }
    }

    @Test
    public void additionalClaims_mayNotSetAReservedClaim() {
        for (String name : new String[] { "iss", "sub", "aud", "exp", "iat", "jti" }) {
            assertEquals(JwtSpec.reservedClaimMessage(name), buildMessage(b -> b.additionalClaims(Map.of(name, "v"))));
        }
        assertEquals(Map.of("scope", "s"), jwtBuilder().additionalClaims(Map.of("scope", "s")).build().getAdditionalClaims());
    }

    @Test
    public void additionalClaims_areLiterals() {
        assertEquals(
            "auth_config.jwt.additional_claims.scope must be a literal value, not a reference",
            buildMessage(b -> b.additionalClaims(Map.of("scope", "${credential.s}")))
        );
        assertEquals(
            "auth_config.jwt.additional_claims.scope must not use ${parameters.*}: auth_config is read from the stored connector only",
            buildMessage(b -> b.additionalClaims(Map.of("scope", "${parameters.s}")))
        );
        assertEquals(
            "Field names in auth_config.jwt.additional_claims must not contain ${",
            buildMessage(b -> b.additionalClaims(Map.of("${credential.n}", "v")))
        );
        Map<String, String> nullValue = new LinkedHashMap<>();
        nullValue.put("scope", null);
        assertEquals("auth_config.jwt.additional_claims.scope must be a string", buildMessage(b -> b.additionalClaims(nullValue)));
    }

    @Test
    public void additionalClaims_areKeptInKeyOrderWhateverOrderTheyArriveIn() throws IOException {
        Map<String, String> scopeFirst = new LinkedHashMap<>();
        scopeFirst.put("scope", "https://www.googleapis.com/auth/cloud-platform");
        scopeFirst.put("tenant", "t1");
        JwtSpec reordered = fullJwt().toBuilder().additionalClaims(scopeFirst).build();
        assertEquals(fullJwt(), reordered);
        assertEquals(List.of("scope", "tenant"), List.copyOf(reordered.getAdditionalClaims().keySet()));
        assertEquals("equal specifications emit the same document", FULL_JWT_JSON, toJson(reordered));
        assertThrows(UnsupportedOperationException.class, () -> reordered.getAdditionalClaims().put("x", "y"));
    }

    @Test
    public void privateKey_isRequiredAndMustBeAReference() {
        assertEquals(JwtSpec.PRIVATE_KEY_REQUIRED_MESSAGE, buildMessage(b -> b.privateKey(null)));
        String pasted = "-----BEGIN PRIVATE KEY-----\nMIIE-not-a-key\n-----END PRIVATE KEY-----";
        String message = buildMessage(b -> b.privateKey(pasted));
        assertEquals(JwtSpec.PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE, message);
        assertFalse(message, message.contains("MIIE"));
        assertEquals(JwtSpec.PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE, buildMessage(b -> b.privateKey("")));
        assertEquals(JwtSpec.PRIVATE_KEY_MUST_BE_REFERENCE_MESSAGE, buildMessage(b -> b.privateKey("${parameters.key}")));
    }

    @Test
    public void toString_omitsClaimValues() {
        JwtSpec spec = jwtBuilder().additionalClaims(Map.of("apikey", "literal-api-key")).build();
        assertFalse(spec.toString(), spec.toString().contains("literal-api-key"));
    }

    @Test
    public void toXContent_emitsTheMembersInSchemaOrderWithTheDefaultsAndClaimsOnlyWhenPresent() throws IOException {
        assertEquals(JWT_JSON, toJson(jwt()));
        assertEquals(FULL_JWT_JSON, toJson(fullJwt()));
    }

    @Test
    public void stream_roundTripIsEqual() throws IOException {
        for (JwtSpec spec : new JwtSpec[] { jwt(), fullJwt() }) {
            assertEquals(spec, roundTrip(spec, Version.CURRENT, JwtSpec::new));
        }
    }

    @Test
    public void parse_appliesTheRulesOfTheBuilder() {
        // The parse reads the shape; the values meet the rules when the block is built, with the same message.
        assertEquals(buildMessage(b -> b.signingAlg("ES256")), parseJwtMessage(JWT_JSON.replace("RS256", "ES256")));
        assertEquals(
            buildMessage(b -> b.additionalClaims(Map.of("exp", "1"))),
            parseJwtMessage(JWT_JSON.replace("\"signing_alg\"", "\"additional_claims\":{\"exp\":\"1\"},\"signing_alg\""))
        );
    }
}
