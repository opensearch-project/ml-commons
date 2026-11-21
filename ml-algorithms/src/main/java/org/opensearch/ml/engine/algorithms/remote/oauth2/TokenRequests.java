/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.time.Instant;
import java.util.Base64;

import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.OAuth2Support;
import org.opensearch.ml.common.connector.auth.PrivateKeys;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;

/**
 * Builds the token request of a client credentials grant or of a JWT bearer grant. Resolved values are never sent
 * raw: the form body percent-encodes them and the Basic value is Base64, so no credential value can start a header.
 */
final class TokenRequests {
    /** The form parameter that carries the assertion of the JWT bearer grant (RFC 7523 section 2.1). */
    static final String ASSERTION_FIELD = "assertion";
    /** The form parameters of client authentication with an assertion (RFC 7521 section 4.2). */
    static final String CLIENT_ASSERTION_TYPE_FIELD = "client_assertion_type";
    static final String CLIENT_ASSERTION_FIELD = "client_assertion";
    /** The {@code client_assertion_type} of a JWT (RFC 7523 section 2.2). */
    static final String CLIENT_ASSERTION_TYPE_JWT_BEARER = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private TokenRequests() {}

    /**
     * A POST to {@code url} with the form body of the grant. The client credentials grant sends the client id and
     * secret in the body or, for {@code client_secret_basic}, in the Basic authorization header; for
     * {@code private_key_jwt}, the client id and a client assertion signed now, with no header and no secret. The JWT
     * bearer grant sends an assertion signed now and the client id as a form parameter when there is one (RFC 7521
     * section 4.2); the assertion is the client authentication, so no header and no secret go with it. Either
     * assertion is addressed to {@code canonicalTokenUrl} unless the block names an audience. {@code scope},
     * {@code audience} and {@code token_request_params} are sent with either grant.
     *
     * @throws IllegalArgumentException with a message of {@link PrivateKeys} when the key the jwt block references
     *     cannot sign the assertion
     */
    static Request build(HttpUrl url, OAuth2AuthConfig config, CredentialSnapshot credentials, String canonicalTokenUrl) {
        boolean jwtBearer = OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER.equals(config.getGrantType());
        FormBody.Builder form = new FormBody.Builder(UTF_8).add(OAuth2AuthConfig.GRANT_TYPE_FIELD, config.getGrantType());
        if (jwtBearer) {
            form.add(ASSERTION_FIELD, JwtAssertionSigner.assertion(config.getJwt(), credentials, canonicalTokenUrl, Instant.now()));
        }
        addIfPresent(form, OAuth2AuthConfig.SCOPE_FIELD, credentials.resolve(config.getScope()));
        addIfPresent(form, OAuth2AuthConfig.AUDIENCE_FIELD, credentials.resolve(config.getAudience()));
        if (config.getTokenRequestParams() != null) {
            config.getTokenRequestParams().forEach(form::add);
        }
        Request.Builder request = new Request.Builder().url(url).header("Accept", "application/json");
        String clientId = credentials.resolve(config.getClientId());
        if (jwtBearer) {
            addIfPresent(form, OAuth2AuthConfig.CLIENT_ID_FIELD, clientId);
        } else if (OAuth2AuthConfig.CLIENT_AUTH_METHOD_PRIVATE_KEY_JWT.equals(config.getClientAuthMethod())) {
            String clientAssertion = JwtAssertionSigner
                .clientAssertion(config.getJwt(), clientId, credentials, canonicalTokenUrl, Instant.now());
            form
                .add(OAuth2AuthConfig.CLIENT_ID_FIELD, clientId)
                .add(CLIENT_ASSERTION_TYPE_FIELD, CLIENT_ASSERTION_TYPE_JWT_BEARER)
                .add(CLIENT_ASSERTION_FIELD, clientAssertion);
        } else if (OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC.equals(config.getClientAuthMethod())) {
            request.header(OAuth2Support.AUTHORIZATION_HEADER, basicAuthorization(clientId, credentials.resolve(config.getClientSecret())));
        } else {
            form
                .add(OAuth2AuthConfig.CLIENT_ID_FIELD, clientId)
                .add(OAuth2AuthConfig.CLIENT_SECRET_FIELD, credentials.resolve(config.getClientSecret()));
        }
        return request.post(form.build()).build();
    }

    /** RFC 6749 section 2.3.1: the id and the secret are form-encoded before they are joined and Base64-encoded. */
    static String basicAuthorization(String clientId, String clientSecret) {
        String pair = URLEncoder.encode(clientId, UTF_8) + ":" + URLEncoder.encode(clientSecret, UTF_8);
        return "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(UTF_8));
    }

    private static void addIfPresent(FormBody.Builder form, String name, String value) {
        if (value != null) {
            form.add(name, value);
        }
    }
}
