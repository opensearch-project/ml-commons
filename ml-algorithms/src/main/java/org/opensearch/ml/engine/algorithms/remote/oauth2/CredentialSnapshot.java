/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.opensearch.ml.common.connector.auth.CredentialReference;
import org.opensearch.ml.common.utils.StringUtils;

/**
 * The values of the credential keys an {@code auth_config} references, read at one point in time. It prints none of
 * them: there is no {@code toString} override, and the class must not become a record or carry a generated
 * {@code toString}.
 */
public final class CredentialSnapshot {
    private final Map<String, String> values;
    private final String fingerprint;

    private CredentialSnapshot(Map<String, String> values) {
        this.values = values;
        this.fingerprint = StringUtils.hashString(StringUtils.MAPPER.writeValueAsString(values));
    }

    /** A snapshot of the values of {@code keys} in {@code credential}; other keys are not copied. */
    public static CredentialSnapshot of(Map<String, String> credential, Set<String> keys) {
        Map<String, String> values = new TreeMap<>();
        for (String key : keys) {
            values.put(key, credential.get(key));
        }
        return new CredentialSnapshot(values);
    }

    /** null for null; a literal unchanged; a {@code ${credential.<key>}} reference replaced by its value. */
    public String resolve(String value) {
        return CredentialReference.isReference(value) ? values.get(CredentialReference.keyOf(value)) : value;
    }

    /** SHA-256 over the referenced keys and their values, in key order. Never logged. */
    public String fingerprint() {
        return fingerprint;
    }
}
