/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.APIKEY_GRANT;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.APIKEY_KEY;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_ID;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_SECRET;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PKCS1_PEM;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PRIVATE_KEY_KEY;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.config;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.extensionGrantConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.jwtBearerConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKey;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKeyCredential;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.privateKeyJwtConfig;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.PrivateKeys;
import org.opensearch.ml.common.utils.StringUtils;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okio.Buffer;

public class TokenRequestsTests {

    private static final HttpUrl URL = HttpUrl.get(PUBLIC_TOKEN_URL);

    /** {@link TokenRequests#build} for {@link #URL}, whose canonical form is {@link OAuth2TestFixtures#PUBLIC_TOKEN_URL}. */
    private static Request build(OAuth2AuthConfig config, CredentialSnapshot credentials) {
        return TokenRequests.build(URL, config, credentials, PUBLIC_TOKEN_URL);
    }

    /** The form members in order, decoded. */
    private static List<String> form(Request request) {
        FormBody body = (FormBody) request.body();
        List<String> members = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            members.add(body.name(i) + "=" + body.value(i));
        }
        return members;
    }

    /** The names of the form members in order. */
    private static List<String> names(Request request) {
        FormBody body = (FormBody) request.body();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            names.add(body.name(i));
        }
        return names;
    }

    /** The decoded value of the form member {@code name}. */
    private static String value(Request request, String name) {
        FormBody body = (FormBody) request.body();
        for (int i = 0; i < body.size(); i++) {
            if (name.equals(body.name(i))) {
                return body.value(i);
            }
        }
        return null;
    }

    private static String rawBody(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
    }

    /** The claims of a compact JWS. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> claims(String assertion) {
        String[] parts = assertion.split("\\.", -1);
        assertEquals(assertion, 3, parts.length);
        return StringUtils.MAPPER.readValue(new String(Base64.getUrlDecoder().decode(parts[1]), UTF_8), Map.class);
    }

    @Test
    public void basicAuthorization_formEncodesTheIdAndTheSecretFirst() {
        assertEquals(
            "Basic bXkrY2xpZW50JTNBMTpwJTQwc3MrdyUyQnJkJTdFJTJGJUMzJUE5Kg==",
            TokenRequests.basicAuthorization("my client:1", "p@ss w+rd~/\u00e9*")
        );
        assertEquals("Basic YWJjOnMzY3IzdA==", TokenRequests.basicAuthorization("abc", "s3cr3t"));
    }

    @Test
    public void build_clientSecretBasic_sendsTheClientInTheAuthorizationHeader() {
        Request request = build(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC), snapshot(Map.of("client_secret", "s3cr3t")));

        assertEquals("POST", request.method());
        assertSame(URL, request.url());
        assertEquals(Set.of("Accept", "Authorization"), request.headers().names());
        assertEquals("application/json", request.header("Accept"));
        assertEquals(TokenRequests.basicAuthorization(CLIENT_ID, "s3cr3t"), request.header("Authorization"));
        assertEquals(MediaType.get("application/x-www-form-urlencoded"), request.body().contentType());
        assertEquals(List.of("grant_type=client_credentials"), form(request));
    }

    @Test
    public void build_clientSecretPost_sendsTheClientInTheBody() {
        Request request = build(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST), snapshot(Map.of("client_secret", "s3cr3t")));

        assertEquals("POST", request.method());
        assertEquals(Set.of("Accept"), request.headers().names());
        assertEquals(List.of("grant_type=client_credentials", "client_id=" + CLIENT_ID, "client_secret=s3cr3t"), form(request));
    }

    @Test
    public void build_sendsScopeAudienceAndParametersWhenPresent() {
        // The parameters in key order, a reference replaced by its value and a literal as written.
        Request withScope = build(
            config(
                CLIENT_AUTH_METHOD_CLIENT_SECRET_POST,
                "'scope':'${credential.scope}','token_request_params':{'x':'y','resource':'r','apikey':'${credential.apikey}'}"
            ),
            snapshot(Map.of("client_secret", "s3cr3t", "scope", "api://model/.default", "apikey", "k-1"))
        );
        assertEquals(
            List
                .of(
                    "grant_type=client_credentials",
                    "scope=api://model/.default",
                    "apikey=k-1",
                    "resource=r",
                    "x=y",
                    "client_id=" + CLIENT_ID,
                    "client_secret=s3cr3t"
                ),
            form(withScope)
        );

        Request withAudience = build(
            config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC, "'audience':'https://api.example'"),
            snapshot(Map.of("client_secret", "s"))
        );
        assertEquals(List.of("grant_type=client_credentials", "audience=https://api.example"), form(withAudience));
    }

    @Test
    public void build_neverSendsACredentialValueRaw() throws IOException {
        String secret = "a\r\nX-Injected: 1";

        Request post = build(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST), snapshot(Map.of("client_secret", secret)));
        String body = rawBody(post);
        assertFalse(body, body.contains("\r") || body.contains("\n"));
        String encoded = body.substring(body.indexOf("client_secret=") + "client_secret=".length());
        assertEquals(secret, URLDecoder.decode(encoded, StandardCharsets.UTF_8));
        assertNull(post.header("X-Injected"));

        Request basic = build(config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC), snapshot(Map.of("client_secret", secret)));
        assertTrue(basic.header("Authorization"), basic.header("Authorization").matches("Basic [A-Za-z0-9+/]+=*"));
        assertFalse(rawBody(basic).contains("\n"));
        assertNull(basic.header("X-Injected"));
    }

    @Test
    public void build_jwtBearer_sendsTheAssertionAndNoClientAuthentication() throws IOException {
        Request request = build(jwtBearerConfig(), privateKey());

        assertEquals("POST", request.method());
        assertSame(URL, request.url());
        assertEquals("the assertion is the client authentication", Set.of("Accept"), request.headers().names());
        assertEquals(MediaType.get("application/x-www-form-urlencoded"), request.body().contentType());
        assertEquals(List.of("grant_type", "assertion"), names(request));
        assertEquals(GRANT_TYPE_JWT_BEARER, value(request, "grant_type"));
        String assertion = value(request, "assertion");
        assertTrue(assertion, assertion.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+"));
        // Signed for the URL the client resolved, and nothing of the key goes with it.
        assertEquals(PUBLIC_TOKEN_URL, claims(assertion).get("aud"));
        String body = rawBody(request);
        assertFalse(body, body.contains("client_secret") || body.contains("PRIVATE") || body.contains("\n"));
    }

    @Test
    public void build_jwtBearer_sendsTheClientIdScopeAudienceAndParametersAsFormMembers() throws IOException {
        Request request = build(
            jwtBearerConfig(
                "'client_id':'${credential.id}','scope':'s','audience':'https://api.example','token_request_params':{'resource':'r'}",
                ""
            ),
            snapshot(Map.of(PRIVATE_KEY_KEY, rsaPkcs8Pem(), "id", CLIENT_ID))
        );

        assertEquals(Set.of("Accept"), request.headers().names());
        assertEquals(List.of("grant_type", "assertion", "scope", "audience", "resource", "client_id"), names(request));
        assertEquals("s", value(request, "scope"));
        assertEquals("https://api.example", value(request, "audience"));
        assertEquals("r", value(request, "resource"));
        assertEquals(CLIENT_ID, value(request, "client_id"));
        assertFalse(rawBody(request).contains("client_secret"));
    }

    @Test
    public void build_privateKeyJwt_sendsTheClientIdAndAClientAssertion_andNoSecret() throws IOException {
        Request request = build(
            privateKeyJwtConfig("${credential.id}", "'scope':'s','audience':'https://api.example','token_request_params':{'resource':'r'}"),
            snapshot(Map.of(PRIVATE_KEY_KEY, rsaPkcs8Pem(), "id", CLIENT_ID))
        );

        assertEquals("the assertion is the client authentication", Set.of("Accept"), request.headers().names());
        assertEquals(
            List.of("grant_type", "scope", "audience", "resource", "client_id", "client_assertion_type", "client_assertion"),
            names(request)
        );
        assertEquals("client_credentials", value(request, "grant_type"));
        assertEquals(CLIENT_ID, value(request, "client_id"));
        assertEquals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", value(request, "client_assertion_type"));
        Map<String, Object> claims = claims(value(request, "client_assertion"));
        assertEquals("the resolved client id", CLIENT_ID, claims.get("iss"));
        assertEquals(CLIENT_ID, claims.get("sub"));
        assertEquals(PUBLIC_TOKEN_URL, claims.get("aud"));
        String body = rawBody(request);
        assertFalse(body, body.contains("client_secret") || body.contains("PRIVATE") || body.contains("\n"));
    }

    @Test
    public void build_extensionGrant_sendsTheResolvedParametersAndNoClientAuthentication() throws IOException {
        CredentialSnapshot apikey = snapshot(Map.of(APIKEY_KEY, CLIENT_SECRET));

        Request request = build(extensionGrantConfig(""), apikey);
        assertEquals("the parameters carry the credential", Set.of("Accept"), request.headers().names());
        assertEquals(List.of("grant_type=" + APIKEY_GRANT, "apikey=" + CLIENT_SECRET), form(request));

        // The client id, when there is one, follows the parameters, as with the JWT bearer grant.
        Request withClientId = build(
            extensionGrantConfig("'client_id':'${credential.id}','scope':'s','audience':'a'"),
            snapshot(Map.of(APIKEY_KEY, CLIENT_SECRET, "id", CLIENT_ID))
        );
        assertEquals(Set.of("Accept"), withClientId.headers().names());
        assertEquals(List.of("grant_type", "scope", "audience", "apikey", "client_id"), names(withClientId));
        assertEquals(CLIENT_ID, value(withClientId, "client_id"));
        String body = rawBody(withClientId);
        assertFalse(body, body.contains("client_secret") || body.contains("assertion"));
    }

    @Test
    public void build_jwtBearer_refusesAKeyTheSignerCannotUse() {
        IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> build(jwtBearerConfig(), snapshot(privateKeyCredential(PKCS1_PEM)))
        );
        assertEquals(PrivateKeys.INVALID_MESSAGE, e.getMessage());
        assertNull(e.getCause());
    }
}
