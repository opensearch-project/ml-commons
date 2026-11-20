/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * How a token endpoint response is read ({@code auth_config.token_response}), for providers that deviate from
 * RFC 6749 section 5.1. Every field has a default except {@code expires_at_field}.
 */
@Getter
@EqualsAndHashCode
@ToString
public final class TokenResponseSpec implements ToXContentObject, Writeable {
    public static final String ACCESS_TOKEN_FIELD_FIELD = "access_token_field";
    public static final String EXPIRES_IN_FIELD_FIELD = "expires_in_field";
    public static final String EXPIRES_AT_FIELD_FIELD = "expires_at_field";
    public static final String DEFAULT_TTL_SECONDS_FIELD = "default_ttl_seconds";
    public static final Set<String> FIELDS = Set
        .of(ACCESS_TOKEN_FIELD_FIELD, EXPIRES_IN_FIELD_FIELD, EXPIRES_AT_FIELD_FIELD, DEFAULT_TTL_SECONDS_FIELD);

    public static final String DEFAULT_ACCESS_TOKEN_FIELD = "access_token";
    public static final String DEFAULT_EXPIRES_IN_FIELD = "expires_in";
    public static final int DEFAULT_TTL_SECONDS = 300;
    public static final int MIN_DEFAULT_TTL_SECONDS = 1;
    public static final int MAX_DEFAULT_TTL_SECONDS = 3600;

    private final String accessTokenField;
    private final String expiresInField;
    private final String expiresAtField;
    private final int defaultTtlSeconds;

    @Builder
    private TokenResponseSpec(String accessTokenField, String expiresInField, String expiresAtField, Integer defaultTtlSeconds) {
        checkNotEmpty(ACCESS_TOKEN_FIELD_FIELD, accessTokenField);
        checkNotEmpty(EXPIRES_IN_FIELD_FIELD, expiresInField);
        checkNotEmpty(EXPIRES_AT_FIELD_FIELD, expiresAtField);
        int ttl = defaultTtlSeconds == null ? DEFAULT_TTL_SECONDS : defaultTtlSeconds;
        if (ttl < MIN_DEFAULT_TTL_SECONDS || ttl > MAX_DEFAULT_TTL_SECONDS) {
            throw new IllegalArgumentException(
                "auth_config.token_response."
                    + DEFAULT_TTL_SECONDS_FIELD
                    + " must be an integer between "
                    + MIN_DEFAULT_TTL_SECONDS
                    + " and "
                    + MAX_DEFAULT_TTL_SECONDS
            );
        }
        this.accessTokenField = accessTokenField == null ? DEFAULT_ACCESS_TOKEN_FIELD : accessTokenField;
        this.expiresInField = expiresInField == null ? DEFAULT_EXPIRES_IN_FIELD : expiresInField;
        this.expiresAtField = expiresAtField;
        this.defaultTtlSeconds = ttl;
    }

    public TokenResponseSpec(StreamInput in) throws IOException {
        this(in.readString(), in.readString(), in.readOptionalString(), in.readInt());
    }

    /** The specification used when {@code auth_config} has no {@code token_response} block. */
    public static TokenResponseSpec defaults() {
        return builder().build();
    }

    /** {@code value}, when present, is not the empty string. */
    private static void checkNotEmpty(String field, String value) {
        if (value != null && value.isEmpty()) {
            throw new IllegalArgumentException("auth_config.token_response." + field + " must not be empty");
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(accessTokenField);
        out.writeString(expiresInField);
        out.writeOptionalString(expiresAtField);
        out.writeInt(defaultTtlSeconds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        return builder.map(document());
    }

    /** The members present, defaults included; {@code expires_at_field} only when configured. */
    Map<String, Object> document() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(ACCESS_TOKEN_FIELD_FIELD, accessTokenField);
        document.put(EXPIRES_IN_FIELD_FIELD, expiresInField);
        if (expiresAtField != null) {
            document.put(EXPIRES_AT_FIELD_FIELD, expiresAtField);
        }
        document.put(DEFAULT_TTL_SECONDS_FIELD, defaultTtlSeconds);
        return document;
    }
}
