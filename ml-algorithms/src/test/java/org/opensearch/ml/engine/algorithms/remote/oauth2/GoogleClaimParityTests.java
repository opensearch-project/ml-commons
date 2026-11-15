/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.opensearch.ml.common.connector.auth.OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.ISSUER;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.TOKEN_RESPONSE;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.connector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.googleConnector;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.json;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.parse;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.rsaPkcs8Pem;
import static org.opensearch.ml.engine.algorithms.remote.oauth2.OAuth2TestFixtures.serviceAccountCredential;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.connector.HttpConnector;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.PrivateKeys;
import org.opensearch.ml.common.utils.StringUtils;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.oauth2.ServiceAccountCredentials;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;

/**
 * A legacy google_cloud service account connector, which used to obtain its token through the Google auth library,
 * obtains it through the shared token engine with the assertion the library would have signed: the same issuer,
 * scope, audience, lifetime, algorithm and no subject. The generic http form of the same account is one and the same
 * configuration to the engine.
 */
public class GoogleClaimParityTests {

    private static final String TOKEN_URL = GoogleCloudConnector.DEFAULT_TOKEN_URI;
    private static final String SCOPE = GoogleCloudConnector.DEFAULT_SCOPE;
    private static final String ACTION_URL =
        "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1/x:generateContent";
    /** The generic form of the account, written as a request writes it. */
    private static final String HTTP_FORM = json(
        "{'type':'oauth2','token_url':'"
            + TOKEN_URL
            + "','grant_type':'"
            + GRANT_TYPE_JWT_BEARER
            + "','client_auth_method':'none','jwt':{'issuer':'${credential.client_email}','lifetime_seconds':3600,"
            + "'additional_claims':{'scope':'"
            + SCOPE
            + "'},'private_key':'${credential.private_key}'}}"
    );

    /** A decoded compact JWS: its header and its claims. */
    private record Jwt(Map<String, Object> header, Map<String, Object> claims) {
        static Jwt decode(String assertion) {
            String[] parts = assertion.split("\\.");
            assertEquals(3, parts.length);
            return new Jwt(jsonObject(parts[0]), jsonObject(parts[1]));
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> jsonObject(String base64Url) {
            return StringUtils.MAPPER.readValue(new String(Base64.getUrlDecoder().decode(base64Url), UTF_8), LinkedHashMap.class);
        }

        long lifetime() {
            return ((Number) claims.get("exp")).longValue() - ((Number) claims.get("iat")).longValue();
        }

        /** The claims that do not depend on when the assertion was signed. */
        Map<String, Object> fixedClaims() {
            Map<String, Object> fixed = new HashMap<>(claims);
            fixed.keySet().removeAll(List.of("iat", "exp", "jti"));
            return fixed;
        }
    }

    /**
     * The assertion the Google auth library sends for the account, configured with {@code tokenServerUri} when there is
     * one: its token request is answered locally and read back.
     */
    private static String libraryAssertion(String tokenServerUri) throws IOException {
        MockLowLevelHttpResponse response = new MockLowLevelHttpResponse().setContentType("application/json").setContent(TOKEN_RESPONSE);
        MockHttpTransport transport = new MockHttpTransport.Builder().setLowLevelHttpResponse(response).build();
        ServiceAccountCredentials.Builder builder = ServiceAccountCredentials
            .newBuilder()
            .setClientEmail(ISSUER)
            .setPrivateKey(PrivateKeys.parsePkcs8(rsaPkcs8Pem()))
            .setScopes(List.of(SCOPE))
            .setHttpTransportFactory(() -> transport);
        if (tokenServerUri != null) {
            builder.setTokenServerUri(URI.create(tokenServerUri));
        }
        builder.build().refreshAccessToken();
        return formOf(transport.getLowLevelHttpRequest().getContentAsString()).get("assertion");
    }

    private static Map<String, String> formOf(String encoded) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : encoded.split("&")) {
            int separator = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, separator), UTF_8), URLDecoder.decode(pair.substring(separator + 1), UTF_8));
        }
        return form;
    }

    private static CredentialSnapshot snapshotOf(Connector connector) {
        return CredentialSnapshot.of(connector.getDecryptedCredential(), connector.getEffectiveAuthConfig().referencedCredentialKeys());
    }

    /** The form the engine sends for the connector's effective configuration. */
    private static Map<String, String> engineForm(Connector connector) {
        return engineForm(connector, TOKEN_URL);
    }

    /** The form the engine sends to {@code tokenUrl}, the connector's effective token URL, for its effective configuration. */
    private static Map<String, String> engineForm(Connector connector, String tokenUrl) {
        Request request = TokenRequests.build(HttpUrl.get(tokenUrl), connector.getEffectiveAuthConfig(), snapshotOf(connector), tokenUrl);
        FormBody body = (FormBody) request.body();
        Map<String, String> form = new LinkedHashMap<>();
        for (int i = 0; i < body.size(); i++) {
            form.put(body.name(i), body.value(i));
        }
        return form;
    }

    /** The key under which the node caches the connector's token, as the provider factory derives it. */
    private static String cacheKey(Connector connector) {
        OAuth2AuthConfig config = connector.getEffectiveAuthConfig();
        return OAuth2TokenCache
            .key(connector.getTenantId(), Strings.toString(MediaTypeRegistry.JSON, config), snapshotOf(connector).fingerprint());
    }

    @Test
    public void theEngineSignsTheClaimsTheGoogleAuthLibrarySigns() throws Exception {
        Jwt library = Jwt.decode(libraryAssertion(null));
        Jwt engine = Jwt.decode(engineForm(googleConnector(null, serviceAccountCredential(null), null, ACTION_URL)).get("assertion"));

        assertEquals("RS256", library.header().get("alg"));
        assertEquals(library.header().get("alg"), engine.header().get("alg"));
        for (String claim : List.of("iss", "scope", "aud")) {
            assertEquals(claim, library.claims().get(claim), engine.claims().get(claim));
        }
        assertEquals(ISSUER, engine.claims().get("iss"));
        assertEquals(SCOPE, engine.claims().get("scope"));
        assertEquals(TOKEN_URL, engine.claims().get("aud"));
        assertEquals(library.lifetime(), engine.lifetime());
        assertEquals(3600L, engine.lifetime());
        assertFalse(library.claims().containsKey("sub"));
        assertFalse(engine.claims().containsKey("sub"));
    }

    @Test
    public void withItsOwnTokenUri_theAssertionIsStillAddressedToTheGoogleTokenEndpoint_asTheLibrarySendsIt() throws Exception {
        String tokenUri = "https://oauth2.example.com/token";
        Jwt library = Jwt.decode(libraryAssertion(tokenUri));
        GoogleCloudConnector legacy = googleConnector(null, serviceAccountCredential(tokenUri), null, ACTION_URL);
        Jwt engine = Jwt.decode(engineForm(legacy, tokenUri).get("assertion"));

        assertEquals(TOKEN_URL, library.claims().get("aud"));
        assertEquals(library.claims().get("aud"), engine.claims().get("aud"));
    }

    @Test
    public void theLegacyAndTheGenericFormOfOneAccount_shareOneCacheEntry_andSendOneForm() {
        Map<String, String> credential = serviceAccountCredential(null);
        GoogleCloudConnector legacy = googleConnector(null, credential, null, ACTION_URL);
        HttpConnector generic = connector(parse(HTTP_FORM), ACTION_URL, credential);

        assertEquals(generic.getEffectiveAuthConfig(), legacy.getEffectiveAuthConfig());
        assertEquals(cacheKey(generic), cacheKey(legacy));

        Map<String, String> legacyForm = engineForm(legacy);
        Map<String, String> genericForm = engineForm(generic);
        assertEquals(List.of("grant_type", "assertion"), List.copyOf(legacyForm.keySet()));
        assertEquals(List.copyOf(legacyForm.keySet()), List.copyOf(genericForm.keySet()));
        assertEquals(GRANT_TYPE_JWT_BEARER, genericForm.get("grant_type"));
        assertEquals(legacyForm.get("grant_type"), genericForm.get("grant_type"));
        Jwt fromLegacy = Jwt.decode(legacyForm.get("assertion"));
        Jwt fromGeneric = Jwt.decode(genericForm.get("assertion"));
        assertEquals(fromLegacy.header(), fromGeneric.header());
        assertEquals(fromLegacy.fixedClaims(), fromGeneric.fixedClaims());
        assertEquals(fromLegacy.lifetime(), fromGeneric.lifetime());
    }
}
