/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.opensearch.ml.common.connector.auth.OAuth2Support;
import org.opensearch.ml.common.connector.auth.TokenResponseSpec;
import org.opensearch.ml.common.exception.MLException;
import org.opensearch.ml.common.utils.StringUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/** Reads the body of a token endpoint response. No message repeats a value of the body. */
final class TokenResponses {
    public static final String INVALID_RESPONSE_DETAIL = "invalid_response (%s)";
    public static final String RESPONSE_TOO_LARGE = "the response is larger than " + OAuth2TokenClient.MAX_RESPONSE_BYTES + " bytes";
    public static final String RESPONSE_NOT_JSON_OBJECT = "the response is not a valid JSON object";
    public static final String NO_ACCESS_TOKEN = "the response has no string member [%s]";
    public static final String NOT_BEARER = "token_type is not bearer";
    public static final String NOT_A_NUMBER = "member [%s] is not a number";
    /** The values an identity provider may send in "error": RFC 6749 section 5.2, plus server_error and temporarily_unavailable. */
    static final Set<String> IDP_ERROR_CODES = Set
        .of(
            "invalid_request",
            "invalid_client",
            "invalid_grant",
            "unauthorized_client",
            "unsupported_grant_type",
            "invalid_scope",
            "server_error",
            "temporarily_unavailable"
        );
    static final String UNKNOWN_ERROR_CODE = "unknown";

    private static final String TOKEN_TYPE_FIELD = "token_type";
    private static final String BEARER = "bearer";
    private static final String ERROR_FIELD = "error";

    private TokenResponses() {}

    /**
     * The token a successful response carries, with its lifetime clamped to [1, maxLifetimeSeconds]. The lifetime comes
     * from the first of {@code expires_in_field}, {@code expires_at_field} (when configured) and
     * {@code default_ttl_seconds} that is present; a member that is JSON null counts as absent.
     *
     * @throws MLException with {@link #INVALID_RESPONSE_DETAIL} and the reason when the body cannot be used
     */
    static OAuth2TokenClient.IssuedToken parse(
        byte[] body,
        TokenResponseSpec spec,
        String tokenUrl,
        long nowEpochSeconds,
        int maxLifetimeSeconds
    ) {
        if (body.length > OAuth2TokenClient.MAX_RESPONSE_BYTES) {
            throw invalid(RESPONSE_TOO_LARGE);
        }
        JsonNode root = readObject(body);
        if (root == null) {
            throw invalid(RESPONSE_NOT_JSON_OBJECT);
        }
        // A token of another type, such as DPoP, is never sent as a bearer token.
        JsonNode tokenType = root.get(TOKEN_TYPE_FIELD);
        if (!isAbsent(tokenType) && !(tokenType.isString() && BEARER.equalsIgnoreCase(tokenType.stringValue()))) {
            throw invalid(NOT_BEARER);
        }
        JsonNode accessToken = root.get(spec.getAccessTokenField());
        if (accessToken == null || !accessToken.isString()) {
            throw invalid(String.format(Locale.ROOT, NO_ACCESS_TOKEN, spec.getAccessTokenField()));
        }
        byte[] token;
        try {
            token = OAuth2Support.sanitizeToken(accessToken.stringValue());
        } catch (IllegalArgumentException e) {
            throw invalid(e.getMessage());
        }
        return new OAuth2TokenClient.IssuedToken(token, lifetimeSeconds(root, spec, nowEpochSeconds, maxLifetimeSeconds), tokenUrl);
    }

    /**
     * The {@code error} member of the body when it is one of {@link #IDP_ERROR_CODES}, exactly; otherwise
     * {@link #UNKNOWN_ERROR_CODE}. Never throws.
     */
    static String errorCode(byte[] body) {
        JsonNode root = readObject(body);
        JsonNode error = root == null ? null : root.get(ERROR_FIELD);
        return error != null && error.isString() && IDP_ERROR_CODES.contains(error.stringValue())
            ? error.stringValue()
            : UNKNOWN_ERROR_CODE;
    }

    private static int lifetimeSeconds(JsonNode root, TokenResponseSpec spec, long nowEpochSeconds, int maxLifetimeSeconds) {
        BigDecimal expiresIn = number(root, spec.getExpiresInField());
        if (expiresIn != null) {
            return clamp(expiresIn, BigDecimal.ZERO, maxLifetimeSeconds);
        }
        BigDecimal expiresAt = spec.getExpiresAtField() == null ? null : number(root, spec.getExpiresAtField());
        if (expiresAt != null) {
            return clamp(expiresAt, BigDecimal.valueOf(nowEpochSeconds), maxLifetimeSeconds);
        }
        return clamp(BigDecimal.valueOf(spec.getDefaultTtlSeconds()), BigDecimal.ZERO, maxLifetimeSeconds);
    }

    /**
     * {@code value - start}, clamped to [1, max]. The bounds are compared before subtracting, which keeps a value such as
     * 1e999999999 cheap.
     */
    private static int clamp(BigDecimal value, BigDecimal start, int max) {
        if (value.compareTo(start.add(BigDecimal.valueOf(max))) >= 0) {
            return max;
        }
        if (value.compareTo(start.add(BigDecimal.ONE)) < 0) {
            return 1;
        }
        return value.subtract(start).intValue();
    }

    /** A JSON number or a numeric string; null when the member is absent or JSON null. */
    private static BigDecimal number(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (isAbsent(node)) {
            return null;
        }
        Optional<BigDecimal> value = node.isString() ? decimal(node.stringValue()) : node.decimalValueOpt();
        return value.orElseThrow(() -> invalid(String.format(Locale.ROOT, NOT_A_NUMBER, field)));
    }

    private static Optional<BigDecimal> decimal(String text) {
        try {
            return Optional.of(new BigDecimal(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** The root object of the body, or null when it is not a JSON object. */
    private static JsonNode readObject(byte[] body) {
        try {
            JsonNode root = StringUtils.MAPPER.readTree(body);
            return root.isObject() ? root : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    private static MLException invalid(String reason) {
        return new MLException(String.format(Locale.ROOT, INVALID_RESPONSE_DETAIL, reason));
    }
}
