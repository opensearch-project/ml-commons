/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import lombok.Getter;

/**
 * The {@code type} of a connector {@code auth_config} block. Values match exactly and case-sensitively, so
 * {@code "OAuth2"} is not a supported type.
 */
public enum ConnectorAuthType {
    OAUTH2("oauth2"),
    NONE("none");

    @Getter
    private final String value;

    ConnectorAuthType(String value) {
        this.value = value;
    }

    /**
     * The type whose value equals {@code value} exactly.
     *
     * @throws IllegalArgumentException when {@code value} is not a supported type
     */
    public static ConnectorAuthType from(String value) {
        for (ConnectorAuthType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported auth_config type [" + value + "]; supported: " + OAUTH2.value + ", " + NONE.value);
    }

    /** True for every type that attaches credentials to requests, that is, for anything other than {@link #NONE}. */
    public boolean isActive() {
        return this != NONE;
    }
}
