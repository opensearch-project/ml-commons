/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.json;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.junit.Test;
import org.opensearch.ml.common.connector.auth.OAuth2Support;
import org.opensearch.ml.common.connector.auth.TokenResponseSpec;
import org.opensearch.ml.common.exception.MLException;

public class TokenResponsesTests {

    private static final TokenResponseSpec DEFAULTS = TokenResponseSpec.defaults();
    private static final TokenResponseSpec EXPIRES_AT = TokenResponseSpec.builder().expiresAtField("expires_at").build();
    private static final long NOW = 1_700_000_000L;
    private static final int CAP = 3600;

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static OAuth2TokenClient.IssuedToken parse(String body, TokenResponseSpec spec, int cap) {
        return TokenResponses.parse(bytes(body), spec, PUBLIC_TOKEN_URL, NOW, cap);
    }

    /** The lifetime of a response holding the token and {@code members} (single-quoted JSON). */
    private static int lifetime(String members, TokenResponseSpec spec, int cap) {
        return parse(json("{'access_token':'tok'" + (members.isEmpty() ? "" : "," + members) + "}"), spec, cap).lifetimeSeconds();
    }

    private static int lifetime(String members) {
        return lifetime(members, DEFAULTS, CAP);
    }

    private static String invalid(byte[] body, TokenResponseSpec spec) {
        MLException e = assertThrows(MLException.class, () -> TokenResponses.parse(body, spec, PUBLIC_TOKEN_URL, NOW, CAP));
        assertNull(e.getCause());
        return e.getMessage();
    }

    private static String invalid(String singleQuoted) {
        return invalid(bytes(json(singleQuoted)), DEFAULTS);
    }

    private static String reason(String reason) {
        return String.format(Locale.ROOT, TokenResponses.INVALID_RESPONSE_DETAIL, reason);
    }

    private static String errorCode(String body) {
        return TokenResponses.errorCode(bytes(body));
    }

    @Test
    public void parse_returnsTheTokenItsLifetimeAndTheTokenUrl() {
        OAuth2TokenClient.IssuedToken token = parse(OAuth2TestFixtures.TOKEN_RESPONSE, DEFAULTS, CAP);

        assertArrayEquals(bytes("tok-1"), token.token());
        assertEquals(3599, token.lifetimeSeconds());
        assertEquals(PUBLIC_TOKEN_URL, token.tokenUrl());
    }

    @Test
    public void parse_acceptsOnlyBearerTokens() {
        for (String tokenType : new String[] { "", "'token_type':'Bearer'", "'token_type':'BEARER'", "'token_type':null" }) {
            assertEquals(tokenType, TokenResponseSpec.DEFAULT_TTL_SECONDS, lifetime(tokenType));
        }
        assertEquals("invalid_response (token_type is not bearer)", invalid("{'access_token':'tok','token_type':'DPoP'}"));
        for (String tokenType : new String[] { "'mac'", "1" }) {
            assertEquals(reason(TokenResponses.NOT_BEARER), invalid("{'access_token':'tok','token_type':" + tokenType + "}"));
        }
    }

    @Test
    public void parse_requiresAPrintableAccessToken() {
        assertEquals(reason("the response has no string member [access_token]"), invalid("{'expires_in':60}"));
        assertEquals(reason(String.format(Locale.ROOT, TokenResponses.NO_ACCESS_TOKEN, "access_token")), invalid("{'access_token':5}"));
        assertEquals(reason(OAuth2Support.TOKEN_EMPTY_MESSAGE), invalid("{'access_token':''}"));
        assertEquals(reason(OAuth2Support.TOKEN_CHARACTER_MESSAGE), invalid("{'access_token':'tok\\r\\nX-Injected: 1'}"));
        assertEquals(reason(OAuth2Support.TOKEN_TOO_LONG_MESSAGE), invalid("{'access_token':'" + "a".repeat(8173) + "'}"));

        TokenResponseSpec custom = TokenResponseSpec.builder().accessTokenField("token").build();
        assertArrayEquals(bytes("tok-2"), parse(json("{'token':'tok-2','access_token':'other'}"), custom, CAP).token());
        assertEquals(
            reason(String.format(Locale.ROOT, TokenResponses.NO_ACCESS_TOKEN, "token")),
            invalid(bytes(json("{'access_token':'tok'}")), custom)
        );
    }

    @Test
    public void parse_readsExpiresInAsANumberOrANumericString() {
        assertEquals(3599, lifetime("'expires_in':3599"));
        assertEquals(3599, lifetime("'expires_in':'3599'"));
        assertEquals(3599, lifetime("'expires_in':3599.7"));
        for (String expired : new String[] { "0", "-5", "-1e9" }) {
            assertEquals(expired, 1, lifetime("'expires_in':" + expired));
        }
        assertEquals(CAP, lifetime("'expires_in':86400"));
        for (String huge : new String[] { "1e30", "'1e999999999'", "9223372036854775807" }) {
            assertEquals(huge, CAP, lifetime("'expires_in':" + huge));
        }
        assertEquals(reason("member [expires_in] is not a number"), invalid("{'access_token':'tok','expires_in':'abc'}"));
        for (String notANumber : new String[] { "true", "' 3599'", "1e400" }) {
            assertEquals(
                notANumber,
                reason(String.format(Locale.ROOT, TokenResponses.NOT_A_NUMBER, "expires_in")),
                invalid("{'access_token':'tok','expires_in':" + notANumber + "}")
            );
        }
    }

    @Test
    public void parse_fallsBackToExpiresAtThenToTheDefaultLifetime() {
        assertEquals(100, lifetime("'expires_at':" + (NOW + 100), EXPIRES_AT, CAP));
        assertEquals(100, lifetime("'expires_at':'" + (NOW + 100) + "'", EXPIRES_AT, CAP));
        assertEquals(1, lifetime("'expires_at':" + (NOW - 100), EXPIRES_AT, CAP));
        assertEquals(CAP, lifetime("'expires_at':'1e999999999'", EXPIRES_AT, CAP));
        assertEquals(60, lifetime("'expires_in':60,'expires_at':" + (NOW + 100), EXPIRES_AT, CAP));
        assertEquals(100, lifetime("'expires_in':null,'expires_at':" + (NOW + 100), EXPIRES_AT, CAP));
        // expires_at is read only when configured.
        assertEquals(TokenResponseSpec.DEFAULT_TTL_SECONDS, lifetime("'expires_at':" + (NOW + 100)));

        assertEquals(300, lifetime(""));
        TokenResponseSpec longDefault = TokenResponseSpec.builder().defaultTtlSeconds(1800).build();
        assertEquals(1800, lifetime("", longDefault, CAP));
        assertEquals(600, lifetime("", longDefault, 600));
    }

    @Test
    public void parse_rejectsABodyThatIsNotAJsonObjectOrIsTooLarge() {
        assertEquals(reason("the response is not a valid JSON object"), invalid(bytes("<html>"), DEFAULTS));
        assertEquals(reason(TokenResponses.RESPONSE_NOT_JSON_OBJECT), invalid(bytes("[]"), DEFAULTS));
        assertEquals(reason(TokenResponses.RESPONSE_NOT_JSON_OBJECT), invalid("{'access_token':'a','access_token':'b'}"));
        assertEquals(reason(TokenResponses.RESPONSE_NOT_JSON_OBJECT), invalid(new byte[0], DEFAULTS));

        assertEquals(
            reason("the response is larger than 65536 bytes"),
            invalid(new byte[OAuth2TokenClient.MAX_RESPONSE_BYTES + 1], DEFAULTS)
        );
    }

    @Test
    public void errorCode_isOneOfTheProviderCodesOrUnknown() {
        for (String code : TokenResponses.IDP_ERROR_CODES) {
            assertEquals(code, errorCode("{\"error\":\"" + code + "\"}"));
        }
        assertEquals("invalid_client", errorCode(json("{'error':'invalid_client','error_description':'MARKER'}")));

        assertEquals("unknown", errorCode("{\"error\":\"INVALID_CLIENT\"}"));
        for (String error : new String[] { "invalid_response", "transport_error", "x".repeat(60_000), "invalid_client\\r\\n" }) {
            assertEquals(TokenResponses.UNKNOWN_ERROR_CODE, errorCode("{\"error\":\"" + error + "\"}"));
        }
        for (String body : new String[] { "{\"error\":5}", "<html>", "" }) {
            assertEquals(body, TokenResponses.UNKNOWN_ERROR_CODE, errorCode(body));
        }
    }
}
