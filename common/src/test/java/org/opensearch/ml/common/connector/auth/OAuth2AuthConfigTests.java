/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.FULL_OAUTH2_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.OAUTH2_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.STORED_WITH_MARKERS_JSON;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.TOKEN_URL;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.document;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.fullOauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.oauth2Builder;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parseMessage;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.parser;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.roundTrip;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.storedWithMarkersAsRead;
import static org.opensearch.ml.common.connector.auth.AuthTestFixtures.toJson;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.opensearch.Version;

public class OAuth2AuthConfigTests {

    /** {@link AuthTestFixtures#OAUTH2_JSON} with one member added or replaced; {@code valueJson} is raw JSON, null removes the member. */
    private static Map<String, Object> oauth2With(String field, String valueJson) {
        Map<String, Object> block = document(OAUTH2_JSON);
        if (valueJson == null) {
            block.remove(field);
        } else {
            block.put(field, document("{'v':" + valueJson + "}").get("v"));
        }
        return block;
    }

    private static Map<String, Object> oauth2Without(String field) {
        return oauth2With(field, null);
    }

    private static IllegalArgumentException canonicalFails(String tokenUrl) {
        return assertThrows(IllegalArgumentException.class, () -> OAuth2AuthConfig.canonicalTokenUrl(tokenUrl));
    }

    /** The message {@code builder} fails to build with. */
    private static String buildMessage(OAuth2AuthConfig.OAuth2AuthConfigBuilder builder) {
        return assertThrows(IllegalArgumentException.class, builder::build).getMessage();
    }

    /** The document {@code config} emits, as a map. */
    private static Map<String, Object> emitted(OAuth2AuthConfig config) throws IOException {
        return document(toJson(config));
    }

    /** The object {@code base} denotes plus an empty-array marker for each of {@code removed}. */
    private static Map<String, Object> withMarkers(String base, String... removed) {
        Map<String, Object> expected = document(base);
        for (String member : removed) {
            expected.put(member, List.of());
        }
        return expected;
    }

    @Test
    public void parse_everySupportedField() {
        OAuth2AuthConfig config = OAuth2AuthConfig
            .parse(
                document(
                    "{'type':'oauth2','token_url':'"
                        + TOKEN_URL
                        + "','grant_type':'urn:ibm:params:oauth:grant-type:apikey',"
                        + "'client_auth_method':'client_secret_post','client_id':'${credential.client_id}','client_secret':'${credential.client_secret}',"
                        + "'scope':'inference.invoke','audience':'https://api.example','token_request_params':{'resource':'https://res.example/'},"
                        + "'token_response':{'access_token_field':'token','expires_in_field':'ttl','expires_at_field':'expiration','default_ttl_seconds':1800}}"
                )
            );
        assertEquals(ConnectorAuthType.OAUTH2, config.getType());
        assertEquals(TOKEN_URL, config.getTokenUrl());
        assertEquals("urn:ibm:params:oauth:grant-type:apikey", config.getGrantType());
        assertEquals("client_secret_post", config.getClientAuthMethod());
        assertEquals("${credential.client_id}", config.getClientId());
        assertEquals("${credential.client_secret}", config.getClientSecret());
        assertEquals("inference.invoke", config.getScope());
        assertEquals("https://api.example", config.getAudience());
        assertEquals(Map.of("resource", "https://res.example/"), config.getTokenRequestParams());
        TokenResponseSpec tokenResponse = config.getTokenResponse();
        assertEquals("token", tokenResponse.getAccessTokenField());
        assertEquals("ttl", tokenResponse.getExpiresInField());
        assertEquals("expiration", tokenResponse.getExpiresAtField());
        assertEquals(1800, tokenResponse.getDefaultTtlSeconds());
        assertEquals(Set.of("client_id", "client_secret"), config.referencedCredentialKeys());
        assertNull(config.getReplaced());

        // The optional members default: the client_credentials grant, no audience, an empty (never null) map, the default token_response.
        OAuth2AuthConfig minimal = OAuth2AuthConfig.parse(oauth2Without("grant_type"));
        assertEquals(OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS, minimal.getGrantType());
        assertNull(minimal.getAudience());
        assertEquals(Map.of(), minimal.getTokenRequestParams());
        assertEquals(TokenResponseSpec.defaults(), minimal.getTokenResponse());
        assertEquals(Set.of("s"), minimal.referencedCredentialKeys());
    }

    @Test
    public void parse_jsonNullMeansAbsent() {
        assertNull(OAuth2AuthConfig.parse(oauth2With("scope", "null")).getScope());
        assertEquals(TokenResponseSpec.defaults(), OAuth2AuthConfig.parse(oauth2With("token_response", "null")).getTokenResponse());
        assertEquals(
            OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS,
            OAuth2AuthConfig.parse(oauth2With("grant_type", "null")).getGrantType()
        );
        assertEquals(Map.of(), OAuth2AuthConfig.parse(oauth2With("token_request_params", "{'x':null}")).getTokenRequestParams());
        assertEquals(OAuth2AuthConfig.NONE, OAuth2AuthConfig.parse(document("{'type':'none','scope':null}")));
    }

    @Test
    public void parse_requiresAStringType() {
        assertEquals("auth_config.type is required", parseMessage(Map.of()));
        assertEquals(OAuth2AuthConfig.TYPE_REQUIRED_MESSAGE, parseMessage(document("{'type':null,'client_id':'a'}")));
        assertEquals("auth_config.type must be a string", parseMessage(document("{'type':1}")));
        assertEquals(
            assertThrows(IllegalArgumentException.class, () -> ConnectorAuthType.from("OAuth2")).getMessage(),
            parseMessage(document("{'type':'OAuth2'}"))
        );
    }

    @Test
    public void parse_fromAParserRequiresAnObject() throws IOException {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> OAuth2AuthConfig.parse(parser("[{\"type\":\"none\"}]"))
        );
        assertEquals(OAuth2AuthConfig.NOT_AN_OBJECT_MESSAGE, e.getMessage());
        assertEquals(oauth2(), OAuth2AuthConfig.parse(parser(OAUTH2_JSON)));
    }

    @Test
    public void parse_unknownTopLevelFieldIsNamed() {
        assertEquals(
            "Unknown auth_config field [token_endpoint_headers]",
            parseMessage(oauth2With("token_endpoint_headers", "{'X-Foo':'bar'}"))
        );
        // Checked before the rule that a none block holds only type.
        assertEquals(OAuth2AuthConfig.unknownFieldMessage("scopes"), parseMessage(document("{'type':'none','scopes':'a'}")));
    }

    @Test
    public void parse_unknownNestedFieldIsNamed() {
        assertEquals(
            "Unknown auth_config.token_response field [expires]",
            parseMessage(oauth2With("token_response", "{'access_token_field':'t','expires':'x'}"))
        );
    }

    @Test
    public void parse_oauth2RequiresClientAuthMethod() {
        String message = parseMessage(oauth2Without("client_auth_method"));
        assertTrue(message, message.startsWith("auth_config.client_auth_method is required for type oauth2"));
    }

    @Test
    public void parse_oauth2RequiresTokenUrl() {
        assertEquals("auth_config.token_url is required for type oauth2", parseMessage(oauth2Without("token_url")));
        assertEquals("auth_config.token_url is required for type oauth2", parseMessage(document("{'type':'oauth2'}")));
    }

    @Test
    public void parse_aLiteralTokenUrlIsCheckedForFormAndKeptAsWritten() {
        String url = "http://good.host/token";
        assertEquals(canonicalFails(url).getMessage(), parseMessage(oauth2With("token_url", "'" + url + "'")));
        assertEquals(
            "https://IdP.example.com:443",
            OAuth2AuthConfig.parse(oauth2With("token_url", "'https://IdP.example.com:443'")).getTokenUrl()
        );
        assertEquals("${credential.token_url}", OAuth2AuthConfig.parse(oauth2With("token_url", "'${credential.token_url}'")).getTokenUrl());
    }

    @Test
    public void parse_clientAuthMethodValues() {
        assertEquals("none", OAuth2AuthConfig.parse(oauth2With("client_auth_method", "'none'")).getClientAuthMethod());
        assertEquals(
            "auth_config.client_auth_method must be one of client_secret_basic, client_secret_post, none",
            parseMessage(oauth2With("client_auth_method", "'Client_Secret_Post'"))
        );
        assertEquals(
            "auth_config.client_auth_method must be a literal value, not a reference",
            parseMessage(oauth2With("client_auth_method", "'${credential.method}'"))
        );
    }

    @Test
    public void parse_jwtAndPrivateKeyJwtAreNotSupported() {
        assertEquals(
            OAuth2AuthConfig.PRIVATE_KEY_JWT_NOT_SUPPORTED_MESSAGE,
            parseMessage(oauth2With("client_auth_method", "'private_key_jwt'"))
        );
        assertEquals(
            OAuth2AuthConfig.JWT_NOT_SUPPORTED_MESSAGE,
            parseMessage(
                oauth2With("jwt", "{'issuer':'app','private_key':'${credential.private_key}','signing_alg':'RS256','lifetime_seconds':60}")
            )
        );
        assertEquals(
            "auth_config.token_request_params.apikey: ${credential.<key>} references in token_request_params are not supported in this version",
            parseMessage(oauth2With("token_request_params", "{'apikey':'${credential.ibm_api_key}'}"))
        );
    }

    @Test
    public void parse_deniedGrantTypes() {
        for (String grant : new String[] {
            "password",
            "authorization_code",
            "refresh_token",
            "urn:ietf:params:oauth:grant-type:device_code",
            "PASSWORD",
            "Refresh_Token" }) {
            String message = parseMessage(oauth2With("grant_type", "'" + grant + "'"));
            assertTrue(message, message.startsWith("auth_config.grant_type "));
            assertTrue(message, message.endsWith(" is not allowed"));
        }
    }

    @Test
    public void parse_vendorGrantTypesAreAccepted() {
        for (String grant : new String[] {
            "client_credentials",
            "urn:ibm:params:oauth:grant-type:apikey",
            "urn:ietf:params:oauth:grant-type:jwt-bearer" }) {
            assertEquals(grant, OAuth2AuthConfig.parse(oauth2With("grant_type", "'" + grant + "'")).getGrantType());
        }
    }

    @Test
    public void parse_reservedTokenRequestParams() {
        for (String key : new String[] {
            "grant_type",
            "client_id",
            "client_secret",
            "assertion",
            "client_assertion",
            "client_assertion_type",
            "scope",
            "audience",
            "Client_Secret",
            "CLIENT_ASSERTION_TYPE" }) {
            assertEquals(
                "auth_config.token_request_params may not set the reserved parameter [" + key + "]",
                parseMessage(oauth2With("token_request_params", "{'" + key + "':'v'}"))
            );
        }
    }

    @Test
    public void parse_tokenRequestParamsShape() {
        assertEquals(
            Map.of("resource", "https://cognitiveservices.azure.com/"),
            OAuth2AuthConfig
                .parse(oauth2With("token_request_params", "{'resource':'https://cognitiveservices.azure.com/'}"))
                .getTokenRequestParams()
        );
        assertEquals(
            "auth_config.token_request_params must be a JSON object",
            parseMessage(oauth2With("token_request_params", "'resource=x'"))
        );
        for (String notAString : new String[] { "{'resource':1}", "{'resource':[]}" }) {
            assertEquals(
                notAString,
                "auth_config.token_request_params.resource must be a string",
                parseMessage(oauth2With("token_request_params", notAString))
            );
        }
        assertEquals(
            "Field names in auth_config.token_request_params must not contain ${",
            parseMessage(oauth2With("token_request_params", "{'${credential.k}':'v'}"))
        );
    }

    @Test
    public void parse_parametersPlaceholdersAreRejectedEverywhere() {
        String[][] cases = {
            { "client_secret", "'${parameters.x}'", "auth_config.client_secret" },
            { "token_url", "'https://${parameters.x}/token'", "auth_config.token_url" },
            { "token_request_params", "{'resource':'${parameters.x}'}", "auth_config.token_request_params.resource" },
            { "token_response", "{'access_token_field':'${parameters.x}'}", "auth_config.token_response.access_token_field" },
            { "grant_type", "'${parameters.x}'", "auth_config.grant_type" } };
        for (String[] c : cases) {
            String message = parseMessage(oauth2With(c[0], c[1]));
            assertEquals(c[2] + " must not use ${parameters.*}: auth_config is read from the stored connector only", message);
            assertFalse(message, message.contains("parameters.x"));
        }
        // A jwt block is rejected before any of its values is read.
        assertEquals(OAuth2AuthConfig.JWT_NOT_SUPPORTED_MESSAGE, parseMessage(oauth2With("jwt", "{'issuer':'${parameters.x}'}")));
    }

    /** {@code leafJson} nested {@code depth} objects deep under keys of {@code keyLength} characters. */
    private static String nested(int depth, int keyLength, String leafJson) {
        String key = "k".repeat(keyLength);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append("{\"").append(key).append("\":");
        }
        return sb.append(leafJson).append("}".repeat(depth)).toString();
    }

    @Test
    public void parse_deepValuesAreRejectedAtTheirRootWithoutBeingWalked() {
        // 400 levels under 1,000-character keys: each value is rejected at its root, and no path down to the leaf is built.
        String deep = nested(400, 1000, "\"${parameters.x}\"");
        assertEquals(OAuth2AuthConfig.JWT_NOT_SUPPORTED_MESSAGE, parseMessage(oauth2With("jwt", deep)));
        // Elsewhere the message is the one a shallow value of the wrong type gets.
        String[][] cases = {
            { "scope", deep, "1" },
            { "token_request_params", "{'resource':" + deep + "}", "{'resource':1}" },
            { "token_response", "{'default_ttl_seconds':" + deep + "}", "{'default_ttl_seconds':true}" } };
        for (String[] c : cases) {
            assertEquals(c[0], parseMessage(oauth2With(c[0], c[2])), parseMessage(oauth2With(c[0], c[1])));
        }
    }

    @Test
    public void parse_otherPlaceholdersMustBeWholeValueCredentialReferences() {
        assertEquals(
            "auth_config.token_url must be a whole-value ${credential.<key>} reference or contain no ${",
            parseMessage(oauth2With("token_url", "'https://${credential.host}/token'"))
        );
    }

    @Test
    public void parse_clientSecretMustBeAReference() {
        assertEquals(
            "auth_config.client_secret must be a ${credential.<key>} reference; put the secret itself in the connector credential",
            parseMessage(oauth2With("client_secret", "'s3cr3t-value'"))
        );
    }

    @Test
    public void parse_stringFieldTypes() {
        for (String notAString : new String[] { "['a','b']", "1" }) {
            assertEquals(notAString, "auth_config.scope must be a string", parseMessage(oauth2With("scope", notAString)));
        }
        assertEquals("auth_config.client_id must be a string", parseMessage(oauth2With("client_id", "1")));
        assertEquals("auth_config.scope must not be empty", parseMessage(oauth2With("scope", "''")));
    }

    @Test
    public void parse_defaultTtlSeconds() {
        for (String value : new String[] { "0", "3601", "1800.5", "-1", "'18x'", "99999999999", "true", "[]" }) {
            String message = parseMessage(oauth2With("token_response", "{'default_ttl_seconds':" + value + "}"));
            assertTrue(value + ": " + message, message.startsWith("auth_config.token_response.default_ttl_seconds must be"));
        }
        // An exact integral number, or the integral string some metadata stores return numbers as.
        for (String value : new String[] { "1800", "1800.0", "1", "3600", "'1800'" }) {
            int expected = (int) Double.parseDouble(value.replace("'", ""));
            OAuth2AuthConfig config = OAuth2AuthConfig.parse(oauth2With("token_response", "{'default_ttl_seconds':" + value + "}"));
            assertEquals(value, expected, config.getTokenResponse().getDefaultTtlSeconds());
        }
    }

    @Test
    public void parse_tokenResponseShape() {
        assertEquals("auth_config.token_response must be a JSON object", parseMessage(oauth2With("token_response", "'x'")));
        assertEquals(
            "auth_config.token_response.expires_in_field must be a string",
            parseMessage(oauth2With("token_response", "{'expires_in_field':3600}"))
        );
    }

    @Test
    public void parse_noneBlockHoldsOnlyType() {
        OAuth2AuthConfig config = OAuth2AuthConfig.parse(document("{'type':'none'}"));
        assertEquals(OAuth2AuthConfig.NONE, config);
        assertNull(config.getTokenUrl());
        assertNull("the client_credentials default applies to oauth2 only", config.getGrantType());
        assertTrue(config.referencedCredentialKeys().isEmpty());

        // Checked before the members themselves: a jwt block under none gets this message, not the jwt one.
        for (String member : new String[] { "'scope':'api'", "'jwt':{'issuer':'x'}" }) {
            assertEquals(member, OAuth2AuthConfig.NONE_WITH_FIELDS_MESSAGE, parseMessage(document("{'type':'none'," + member + "}")));
        }
    }

    @Test
    public void build_noneHoldsOnlyType() {
        // The rule parse applies also holds for a configuration built in code, which is stored without a parse.
        OAuth2AuthConfig.OAuth2AuthConfigBuilder[] withMember = {
            OAuth2AuthConfig.NONE.toBuilder().scope("api"),
            OAuth2AuthConfig.NONE.toBuilder().tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build()) };
        for (OAuth2AuthConfig.OAuth2AuthConfigBuilder builder : withMember) {
            assertEquals(
                OAuth2AuthConfig.NONE_WITH_FIELDS_MESSAGE,
                assertThrows(IllegalArgumentException.class, builder::build).getMessage()
            );
        }
        // An empty map and the default token_response are absent members; the replaced configuration is not a member.
        assertEquals(
            OAuth2AuthConfig.NONE,
            OAuth2AuthConfig.NONE.toBuilder().tokenRequestParams(Map.of()).tokenResponse(TokenResponseSpec.defaults()).build()
        );
        assertEquals(OAuth2AuthConfig.NONE, OAuth2AuthConfig.NONE.replacing(fullOauth2()));
    }

    @Test
    public void build_appliesTheValueRulesOfParse() throws IOException {
        // A configuration built in code is stored without a parse, so what parse refuses the builder refuses, with the same message.
        assertEquals(parseMessage(oauth2Without("client_auth_method")), buildMessage(oauth2Builder().clientAuthMethod(null)));
        assertEquals("auth_config.token_url is required for type oauth2", buildMessage(oauth2Builder().tokenUrl(null)));
        assertEquals(
            canonicalFails("http://good.host/token").getMessage(),
            buildMessage(oauth2Builder().tokenUrl("http://good.host/token"))
        );
        assertEquals(
            OAuth2AuthConfig.PRIVATE_KEY_JWT_NOT_SUPPORTED_MESSAGE,
            buildMessage(oauth2Builder().clientAuthMethod("private_key_jwt"))
        );
        assertEquals("auth_config.grant_type [password] is not allowed", buildMessage(oauth2Builder().grantType("password")));
        assertEquals(OAuth2AuthConfig.CLIENT_SECRET_MUST_BE_REFERENCE_MESSAGE, buildMessage(oauth2Builder().clientSecret("s3cr3t-value")));
        assertEquals(parseMessage(oauth2With("scope", "''")), buildMessage(oauth2Builder().scope("")));
        assertEquals(
            "auth_config.token_request_params may not set the reserved parameter [Client_Assertion]",
            buildMessage(oauth2Builder().tokenRequestParams(Map.of("Client_Assertion", "v")))
        );
        assertEquals(
            "auth_config.token_request_params.apikey: ${credential.<key>} references in token_request_params are not supported in this version",
            buildMessage(oauth2Builder().tokenRequestParams(Map.of("apikey", "${credential.k}")))
        );
        assertEquals(
            "auth_config.scope must not use ${parameters.*}: auth_config is read from the stored connector only",
            buildMessage(oauth2Builder().scope("${parameters.x}"))
        );
        assertEquals(
            "auth_config.token_response.access_token_field must be a literal value, not a reference",
            buildMessage(oauth2Builder().tokenResponse(TokenResponseSpec.builder().accessTokenField("${credential.f}").build()))
        );
        // What the builder accepts reads back from the document it emits and from the stream.
        assertEquals(fullOauth2(), OAuth2AuthConfig.fromStored(emitted(fullOauth2())));
        assertEquals(fullOauth2(), roundTrip(fullOauth2(), Version.CURRENT, OAuth2AuthConfig::new));
    }

    @Test
    public void build_refusesAnEmptyMember() {
        Map<String, OAuth2AuthConfig.OAuth2AuthConfigBuilder> withEmptyMember = new LinkedHashMap<>();
        withEmptyMember.put("token_url", oauth2Builder().tokenUrl(""));
        withEmptyMember.put("grant_type", oauth2Builder().grantType(""));
        withEmptyMember.put("client_auth_method", oauth2Builder().clientAuthMethod(""));
        withEmptyMember.put("client_id", oauth2Builder().clientId(""));
        withEmptyMember.put("client_secret", oauth2Builder().clientSecret(""));
        withEmptyMember.put("scope", oauth2Builder().scope(""));
        withEmptyMember.put("audience", oauth2Builder().audience(""));
        for (Map.Entry<String, OAuth2AuthConfig.OAuth2AuthConfigBuilder> member : withEmptyMember.entrySet()) {
            assertEquals("auth_config." + member.getKey() + " must not be empty", buildMessage(member.getValue()));
        }
    }

    @Test
    public void tokenRequestParams_areKeptInKeyOrderWhateverOrderTheyArriveIn() throws IOException {
        Map<String, String> xFirst = new LinkedHashMap<>();
        xFirst.put("x", "y");
        xFirst.put("resource", "r");
        Map<String, Object> block = document(FULL_OAUTH2_JSON);
        block.put(OAuth2AuthConfig.TOKEN_REQUEST_PARAMS_FIELD, xFirst);
        for (OAuth2AuthConfig config : new OAuth2AuthConfig[] {
            fullOauth2().toBuilder().tokenRequestParams(xFirst).build(),
            OAuth2AuthConfig.parse(block) }) {
            assertEquals(fullOauth2(), config);
            assertEquals(List.of("resource", "x"), List.copyOf(config.getTokenRequestParams().keySet()));
            // Equal configurations emit the same document.
            assertEquals(FULL_OAUTH2_JSON, toJson(config));
            assertEquals(fullOauth2().referencedCredentialKeys(), config.referencedCredentialKeys());
            assertEquals(FULL_OAUTH2_JSON, toJson(roundTrip(config, Version.CURRENT, OAuth2AuthConfig::new)));
        }
    }

    @Test
    public void toString_omitsTokenRequestParamValues() {
        OAuth2AuthConfig config = OAuth2AuthConfig.parse(oauth2With("token_request_params", "{'apikey':'literal-api-key'}"));
        assertFalse(config.toString(), config.toString().contains("literal-api-key"));
    }

    @Test
    public void referencedCredentialKeys_collectsEveryReference() {
        OAuth2AuthConfig config = OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl("${credential.token_uri}")
            .clientAuthMethod("none")
            .clientId("${credential.client_id}")
            .clientSecret("${credential.client_secret}")
            .scope("${credential.scope}")
            .audience("${credential.audience}")
            .tokenRequestParams(Map.of("resource", "literal"))
            .build();
        assertEquals(Set.of("token_uri", "client_id", "client_secret", "scope", "audience"), config.referencedCredentialKeys());
    }

    @Test
    public void fromStored_dropsRemovalMarkersAtEveryDepthAndReadsIntegralStrings() throws IOException {
        assertEquals(storedWithMarkersAsRead(), OAuth2AuthConfig.fromStored(document(STORED_WITH_MARKERS_JSON)));
        // What a replacement emits reads back as the replacement; a none block whose other members are all markers is none.
        assertEquals(oauth2(), OAuth2AuthConfig.fromStored(emitted(oauth2().replacing(fullOauth2()))));
        assertEquals(OAuth2AuthConfig.NONE, OAuth2AuthConfig.fromStored(emitted(OAuth2AuthConfig.NONE.replacing(fullOauth2()))));
        // Once the markers are gone the read is the request parse: parsed as written, [] is not a string.
        assertThrows(IllegalArgumentException.class, () -> OAuth2AuthConfig.parse(document(STORED_WITH_MARKERS_JSON)));
        assertThrows(IllegalArgumentException.class, () -> OAuth2AuthConfig.fromStored(document("{'type':'none','scope':'a'}")));
    }

    @Test
    public void toXContent_emitsThePresentMembersInSchemaOrder() throws IOException {
        assertEquals(FULL_OAUTH2_JSON, toJson(fullOauth2()));
        assertEquals("{\"type\":\"none\"}", toJson(OAuth2AuthConfig.NONE));
    }

    @Test
    public void toXContent_emitsTheGrantTypeDefaultAndOmitsEmptyParamsAndADefaultTokenResponse() throws IOException {
        // The emitted document has the grant_type default and omits the default token_request_params and token_response.
        OAuth2AuthConfig parsed = OAuth2AuthConfig.parse(document(OAUTH2_JSON.replace("\"grant_type\":\"client_credentials\",", "")));
        assertEquals(OAUTH2_JSON, toJson(parsed));
        // token_url is emitted as written, not canonicalized.
        assertEquals(
            "https://IdP.example.com:443",
            emitted(oauth2Builder().tokenUrl("https://IdP.example.com:443").build()).get(OAuth2AuthConfig.TOKEN_URL_FIELD)
        );
        // A token_response that differs from the defaults in one member is emitted in full.
        assertEquals(
            document("{'access_token_field':'access_token','expires_in_field':'expires_in','default_ttl_seconds':600}"),
            emitted(oauth2Builder().tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build()).build())
                .get(OAuth2AuthConfig.TOKEN_RESPONSE_FIELD)
        );
    }

    @Test
    public void replacing_noneOverAnActiveConfigurationMarksEveryMember() throws IOException {
        OAuth2AuthConfig replacement = OAuth2AuthConfig.NONE.replacing(fullOauth2());
        assertEquals(
            withMarkers(
                "{'type':'none'}",
                "token_url",
                "grant_type",
                "client_auth_method",
                "client_id",
                "client_secret",
                "scope",
                "audience",
                "token_request_params",
                "token_response"
            ),
            emitted(replacement)
        );
        assertEquals("the replaced configuration is not part of equality", OAuth2AuthConfig.NONE, replacement);
        assertEquals(fullOauth2(), replacement.getReplaced());
    }

    @Test
    public void replacing_marksAtTheHighestMissingLevelAndDescendsIntoNestedObjects() throws IOException {
        assertEquals(
            withMarkers(OAUTH2_JSON, "scope", "audience", "token_request_params", "token_response"),
            emitted(oauth2().replacing(fullOauth2()))
        );
        OAuth2AuthConfig narrowed = oauth2Builder()
            .scope("inference.invoke")
            .audience("https://api.example")
            .tokenRequestParams(Map.of("resource", "r"))
            .tokenResponse(TokenResponseSpec.builder().defaultTtlSeconds(600).build())
            .build();
        Map<String, Object> document = emitted(narrowed.replacing(fullOauth2()));
        assertEquals(withMarkers("{'resource':'r'}", "x"), document.get(OAuth2AuthConfig.TOKEN_REQUEST_PARAMS_FIELD));
        assertEquals(
            withMarkers(
                "{'access_token_field':'access_token','expires_in_field':'expires_in','default_ttl_seconds':600}",
                "expires_at_field"
            ),
            document.get(OAuth2AuthConfig.TOKEN_RESPONSE_FIELD)
        );
    }

    @Test
    public void replacing_setsNoMarkersWhenNothingIsDropped() throws IOException {
        assertEquals(FULL_OAUTH2_JSON, toJson(fullOauth2().replacing(oauth2())));
        assertEquals(FULL_OAUTH2_JSON, toJson(fullOauth2().replacing(fullOauth2())));
        assertEquals("nothing stored", FULL_OAUTH2_JSON, toJson(fullOauth2().replacing(null)));
        assertEquals("{\"type\":\"none\"}", toJson(OAuth2AuthConfig.NONE.replacing(OAuth2AuthConfig.NONE)));
    }

    @Test
    public void dropsMembers_isTrueWhenTheStoredConfigurationHasAMemberAtAnyDepthThatThisOneLacks() {
        assertFalse(fullOauth2().dropsMembers(fullOauth2()));
        assertFalse(fullOauth2().dropsMembers(oauth2()));
        assertTrue(oauth2().dropsMembers(fullOauth2()));
        assertTrue(OAuth2AuthConfig.NONE.dropsMembers(oauth2()));
        assertFalse(oauth2().dropsMembers(OAuth2AuthConfig.NONE));
        OAuth2AuthConfig withoutX = fullOauth2().toBuilder().tokenRequestParams(Map.of("resource", "r")).build();
        assertTrue(withoutX.dropsMembers(fullOauth2()));
        assertFalse(fullOauth2().dropsMembers(withoutX));
    }

    @Test
    public void stream_roundTripIsEqualAndDropsTheReplacedConfiguration() throws IOException {
        for (OAuth2AuthConfig config : new OAuth2AuthConfig[] { fullOauth2(), oauth2(), OAuth2AuthConfig.NONE }) {
            assertEquals(config, roundTrip(config, Version.CURRENT, OAuth2AuthConfig::new));
        }
        OAuth2AuthConfig read = roundTrip(oauth2().replacing(fullOauth2()), Version.CURRENT, OAuth2AuthConfig::new);
        assertNull(read.getReplaced());
        assertEquals(OAUTH2_JSON, toJson(read));
    }

    @Test
    public void canonicalTokenUrl_removesTheDefaultPortOnly() {
        assertEquals("https://good.host/token", OAuth2AuthConfig.canonicalTokenUrl("https://good.host/token"));
        assertEquals(
            OAuth2AuthConfig.canonicalTokenUrl("https://good.host/token"),
            OAuth2AuthConfig.canonicalTokenUrl("https://good.host:443/token")
        );
        assertEquals("https://good.host:8443/token", OAuth2AuthConfig.canonicalTokenUrl("https://good.host:8443/token"));
        assertEquals("https://oauth2.googleapis.com/token", OAuth2AuthConfig.canonicalTokenUrl("https://oauth2.googleapis.com:443/token"));
    }

    @Test
    public void canonicalTokenUrl_lowerCasesSchemeAndHostAndKeepsThePath() {
        assertEquals("https://good.host/Token", OAuth2AuthConfig.canonicalTokenUrl("https://GOOD.HOST/Token"));
        assertEquals("https://good.host/Token", OAuth2AuthConfig.canonicalTokenUrl("HTTPS://Good.Host/Token"));
        assertEquals("https://good.host/a/../b%2Fc", OAuth2AuthConfig.canonicalTokenUrl("https://good.host/a/../b%2Fc"));
        assertEquals("an empty path ends in /", "https://good.host/", OAuth2AuthConfig.canonicalTokenUrl("https://good.host"));
        assertEquals("https://good.host/", OAuth2AuthConfig.canonicalTokenUrl("https://GOOD.host:443"));
    }

    @Test
    public void canonicalTokenUrl_writesAnIpv6LiteralInItsCompressedForm() {
        String[][] cases = {
            { "https://[2001:db8:0:0:0:0:0:1]/token", "https://[2001:db8::1]/token" },
            { "https://[2001:DB8::1]/token", "https://[2001:db8::1]/token" },
            { "https://[::1]:443/token", "https://[::1]/token" },
            { "https://[2001:db8::1]:8443/token", "https://[2001:db8::1]:8443/token" },
            // The longer run of zero groups is compressed; a single zero group is written as 0.
            { "https://[2001:db8:0:1:0:0:0:1]/token", "https://[2001:db8:0:1::1]/token" },
            { "https://[2001:db8:1:0:1:0:1:1]/token", "https://[2001:db8:1:0:1:0:1:1]/token" },
            // The first of two runs of the same length.
            { "https://[1:0:0:2:0:0:3:0]/token", "https://[1::2:0:0:3:0]/token" },
            { "https://[0:0:0:0:0:0:0:0]/token", "https://[::]/token" },
            // An IPv4-mapped address is written as the IPv4 address.
            { "https://[::ffff:192.0.2.1]:8443/token", "https://192.0.2.1:8443/token" } };
        for (String[] c : cases) {
            assertEquals(c[0], c[1], OAuth2AuthConfig.canonicalTokenUrl(c[0]));
        }
    }

    @Test
    public void canonicalTokenUrl_rejectsUnsafeForms() {
        String[][] cases = {
            { "https://good.host@evil/", "auth_config.token_url must not contain user information" },
            { "https://good.host/token#x", "auth_config.token_url must not contain a fragment" },
            { "https://good.host/token#", "auth_config.token_url must not contain a fragment" },
            { "https://good.host/token?a=b", "auth_config.token_url must not contain a query string" },
            { "https://good.host/token?", "auth_config.token_url must not contain a query string" },
            // The host is evil.example and ".okta.com/oauth2/x/v1/token" is the query.
            { "https://evil.example?.okta.com/oauth2/x/v1/token", "auth_config.token_url must not contain a query string" },
            { "http://good.host/token", "auth_config.token_url must use https" },
            { "good.host/token", OAuth2AuthConfig.TOKEN_URL_MUST_USE_HTTPS_MESSAGE },
            { "https:///token", "auth_config.token_url must name a host" },
            { "https://my_host/token", OAuth2AuthConfig.TOKEN_URL_MUST_NAME_A_HOST_MESSAGE },
            { "https://[fe80::1%25eth0]/token", OAuth2AuthConfig.TOKEN_URL_SCOPED_IPV6_MESSAGE },
            { "https://[fe80::1%eth0]/token", OAuth2AuthConfig.TOKEN_URL_SCOPED_IPV6_MESSAGE },
            { "https:good.host", OAuth2AuthConfig.TOKEN_URL_MUST_NAME_A_HOST_MESSAGE },
            { "https://good host/token", "auth_config.token_url is not a valid URI" },
            { "https://${credential.h}/token", "auth_config.token_url is not a valid URI" },
            { "", "auth_config.token_url is required" } };
        for (String[] c : cases) {
            IllegalArgumentException e = canonicalFails(c[0]);
            assertEquals(c[0], c[1], e.getMessage());
            assertNull("the message is the whole report; no cause carries the input", e.getCause());
        }
        canonicalFails(null);
    }
}
