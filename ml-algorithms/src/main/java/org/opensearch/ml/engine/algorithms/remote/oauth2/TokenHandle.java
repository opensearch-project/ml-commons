/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import java.util.function.BooleanSupplier;

import org.opensearch.OpenSearchStatusException;

/**
 * The access token one request uses: a private copy, unaffected by removing or overwriting cached bytes. It prints no
 * field: there is no {@code toString} override, and the class must not become a record or carry a generated
 * {@code toString}.
 */
public final class TokenHandle {
    // The cache entry and the token object the value came from, compared by identity.
    final OAuth2TokenCache.Slot slot;
    final OAuth2TokenCache.Token token;
    private final String value;
    private final BooleanSupplier authEnabled;

    TokenHandle(String value, OAuth2TokenCache.Slot slot, OAuth2TokenCache.Token token, BooleanSupplier authEnabled) {
        this.value = value;
        this.slot = slot;
        this.token = token;
        this.authEnabled = authEnabled;
    }

    /**
     * @throws OpenSearchStatusException 403 naming plugins.ml_commons.connector.auth_enabled when it was turned off after
     *     the token was obtained
     */
    public String value() {
        if (!authEnabled.getAsBoolean()) {
            throw OAuth2TokenCache.authDisabled();
        }
        return value;
    }
}
