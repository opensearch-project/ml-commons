/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_POST;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.CLIENT_ID;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.PUBLIC_TOKEN_URL;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.config;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.snapshot;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okio.Buffer;

public class TokenRequestsTests {

    private static final HttpUrl URL = HttpUrl.get(PUBLIC_TOKEN_URL);

    /** The form members in order, decoded. */
    private static List<String> form(Request request) {
        FormBody body = (FormBody) request.body();
        List<String> members = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
            members.add(body.name(i) + "=" + body.value(i));
        }
        return members;
    }

    private static String rawBody(Request request) throws IOException {
        Buffer buffer = new Buffer();
        request.body().writeTo(buffer);
        return buffer.readUtf8();
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
        Request request = TokenRequests
            .build(URL, config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC), snapshot(Map.of("client_secret", "s3cr3t")));

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
        Request request = TokenRequests
            .build(URL, config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST), snapshot(Map.of("client_secret", "s3cr3t")));

        assertEquals("POST", request.method());
        assertEquals(Set.of("Accept"), request.headers().names());
        assertEquals(List.of("grant_type=client_credentials", "client_id=" + CLIENT_ID, "client_secret=s3cr3t"), form(request));
    }

    @Test
    public void build_sendsScopeAudienceAndParametersWhenPresent() {
        Request withScope = TokenRequests
            .build(
                URL,
                config(
                    CLIENT_AUTH_METHOD_CLIENT_SECRET_POST,
                    "'scope':'${credential.scope}','token_request_params':{'resource':'r','x':'y'}"
                ),
                snapshot(Map.of("client_secret", "s3cr3t", "scope", "api://model/.default"))
            );
        assertEquals(
            List
                .of(
                    "grant_type=client_credentials",
                    "scope=api://model/.default",
                    "resource=r",
                    "x=y",
                    "client_id=" + CLIENT_ID,
                    "client_secret=s3cr3t"
                ),
            form(withScope)
        );

        Request withAudience = TokenRequests
            .build(
                URL,
                config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC, "'audience':'https://api.example'"),
                snapshot(Map.of("client_secret", "s"))
            );
        assertEquals(List.of("grant_type=client_credentials", "audience=https://api.example"), form(withAudience));
    }

    @Test
    public void build_neverSendsACredentialValueRaw() throws IOException {
        String secret = "a\r\nX-Injected: 1";

        Request post = TokenRequests.build(URL, config(CLIENT_AUTH_METHOD_CLIENT_SECRET_POST), snapshot(Map.of("client_secret", secret)));
        String body = rawBody(post);
        assertFalse(body, body.contains("\r") || body.contains("\n"));
        String encoded = body.substring(body.indexOf("client_secret=") + "client_secret=".length());
        assertEquals(secret, URLDecoder.decode(encoded, StandardCharsets.UTF_8));
        assertNull(post.header("X-Injected"));

        Request basic = TokenRequests.build(URL, config(CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC), snapshot(Map.of("client_secret", secret)));
        assertTrue(basic.header("Authorization"), basic.header("Authorization").matches("Basic [A-Za-z0-9+/]+=*"));
        assertFalse(rawBody(basic).contains("\n"));
        assertNull(basic.header("X-Injected"));
    }
}
