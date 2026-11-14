/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

/**
 * The only placeholder form allowed inside {@code auth_config}: a whole value of the form
 * {@code ${credential.<key>}}. Callers reject any other string that contains <code>${</code>, which covers
 * {@code ${parameters.x}}, an escaped {@code $${credential.a}}, and references embedded in a longer string such as
 * {@code https://${credential.h}/t} or {@code Bearer ${credential.k}}.
 */
public final class CredentialReference {
    public static final String PREFIX = "${credential.";
    public static final String SUFFIX = "}";

    private CredentialReference() {}

    /** True when {@code value} is exactly one {@code ${credential.<key>}} reference. */
    public static boolean isReference(String value) {
        return extractKey(value) != null;
    }

    /**
     * The credential key named by a whole-value reference.
     *
     * @throws IllegalArgumentException when {@code value} is not exactly one {@code ${credential.<key>}} reference.
     *     The message does not repeat the value.
     */
    public static String keyOf(String value) {
        String key = extractKey(value);
        if (key == null) {
            throw new IllegalArgumentException("Expected a whole-value ${credential.<key>} reference");
        }
        return key;
    }

    private static String extractKey(String value) {
        if (value == null || !value.startsWith(PREFIX) || !value.endsWith(SUFFIX)) {
            return null;
        }
        String key = value.substring(PREFIX.length(), value.length() - SUFFIX.length());
        return isValidKey(key) ? key : null;
    }

    private static boolean isValidKey(String key) {
        return key != null && !key.isEmpty() && !key.contains(SUFFIX) && !key.contains("${") && !key.contains(":-");
    }
}
