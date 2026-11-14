/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import java.util.Map;
import java.util.Set;

import org.opensearch.ml.common.connector.Connector;

/**
 * Write-time check of the {@code auth_config} a connector holds: every credential key it references must be a key of
 * the connector credential. Every write path runs it on the connector it is about to store, after the request-level
 * checks of {@code ConnectorProtocolValidator}.
 */
public final class AuthConfigValidator {

    private AuthConfigValidator() {}

    /** The message for a reference to credential {@code key}, which the connector credential does not contain. */
    public static String missingCredentialKeyMessage(String key) {
        return "auth_config references credential key [" + key + "], which the connector credential does not contain";
    }

    /**
     * Validates the {@code auth_config} {@code connector} holds; a connector without one passes.
     *
     * @param connector the connector as it will be stored
     * @param credentialKeys the credential keys the stored connector will have; references must name one of them
     * @throws IllegalArgumentException when the configuration is invalid
     */
    public static void validate(Connector connector, Set<String> credentialKeys) {
        OAuth2AuthConfig config = connector.getAuthConfig();
        if (config == null) {
            return;
        }
        // Sorted, so the first missing key is reported deterministically.
        for (String key : config.referencedCredentialKeys()) {
            if (!credentialKeys.contains(key)) {
                throw new IllegalArgumentException(missingCredentialKeyMessage(key));
            }
        }
    }

    /**
     * Whether {@link #validate} would accept {@code connector}. Never throws: any exception counts as a failure. Used
     * to tell whether a stored configuration already failed before an update.
     */
    public static boolean passes(Connector connector, Set<String> credentialKeys) {
        try {
            validate(connector, credentialKeys);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@link #validate} for the connector an update is about to write. An invalid configuration is let through, for
     * the caller to write unchanged and warn about, when the request touches neither {@code auth_config} nor the
     * protocol ({@code authOrProtocolTouched}) and the stored configuration already failed ({@code storedPassed}).
     *
     * @return true when the configuration fails validation but the write may proceed; false when it passes
     * @throws IllegalArgumentException when the configuration is invalid and the update is not of that kind
     */
    public static boolean validateOrGrandfather(
        Connector connector,
        Set<String> credentialKeys,
        boolean authOrProtocolTouched,
        boolean storedPassed
    ) {
        try {
            validate(connector, credentialKeys);
            return false;
        } catch (IllegalArgumentException e) {
            if (!authOrProtocolTouched && !storedPassed) {
                return true;
            }
            throw e;
        }
    }

    /** A copy of the keys of {@code credential}; empty for a null map. */
    public static Set<String> keysOf(Map<String, String> credential) {
        return credential == null ? Set.of() : Set.copyOf(credential.keySet());
    }
}
