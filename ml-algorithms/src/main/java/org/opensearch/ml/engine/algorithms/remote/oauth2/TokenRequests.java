/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URLEncoder;
import java.util.Base64;

import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.connector.auth.OAuth2Support;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Request;

/**
 * Builds the token request of a client credentials grant. Resolved values are never sent raw: the form body
 * percent-encodes them and the Basic value is Base64, so no credential value can start a header.
 */
final class TokenRequests {

    private TokenRequests() {}

    /** A POST to {@code url} with the form body and, for {@code client_secret_basic}, the Basic authorization header. */
    static Request build(HttpUrl url, OAuth2AuthConfig config, CredentialSnapshot credentials) {
        FormBody.Builder form = new FormBody.Builder(UTF_8).add(OAuth2AuthConfig.GRANT_TYPE_FIELD, config.getGrantType());
        addIfPresent(form, OAuth2AuthConfig.SCOPE_FIELD, credentials.resolve(config.getScope()));
        addIfPresent(form, OAuth2AuthConfig.AUDIENCE_FIELD, credentials.resolve(config.getAudience()));
        if (config.getTokenRequestParams() != null) {
            config.getTokenRequestParams().forEach(form::add);
        }
        Request.Builder request = new Request.Builder().url(url).header("Accept", "application/json");
        String clientId = credentials.resolve(config.getClientId());
        String clientSecret = credentials.resolve(config.getClientSecret());
        if (OAuth2AuthConfig.CLIENT_AUTH_METHOD_CLIENT_SECRET_BASIC.equals(config.getClientAuthMethod())) {
            request.header(OAuth2Support.AUTHORIZATION_HEADER, basicAuthorization(clientId, clientSecret));
        } else {
            form.add(OAuth2AuthConfig.CLIENT_ID_FIELD, clientId).add(OAuth2AuthConfig.CLIENT_SECRET_FIELD, clientSecret);
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
