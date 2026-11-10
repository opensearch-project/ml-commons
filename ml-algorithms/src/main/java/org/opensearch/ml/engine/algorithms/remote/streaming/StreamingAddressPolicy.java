/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.streaming;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import lombok.Getter;

/**
 * The connector address settings that streaming requests must honour, matching what the non-streaming client applies:
 * {@code plugins.ml_commons.connector.private_ip_enabled}, {@code plugins.ml_commons.trusted_connector_private_endpoints_regex}
 * and {@code plugins.ml_commons.connector.restricted_ip_patterns}.
 */
@Getter
public final class StreamingAddressPolicy {
    /** The settings' defaults: private addresses refused, no private endpoint patterns, no restricted patterns. */
    public static final StreamingAddressPolicy DEFAULT = new StreamingAddressPolicy(false, null, null);

    private final boolean privateIpEnabled;
    private final List<Pattern> trustedPrivateEndpoints;
    private final List<Pattern> restrictedIpPatterns;

    public StreamingAddressPolicy(boolean privateIpEnabled, List<Pattern> trustedPrivateEndpoints, List<Pattern> restrictedIpPatterns) {
        this.privateIpEnabled = privateIpEnabled;
        this.trustedPrivateEndpoints = trustedPrivateEndpoints == null ? Collections.emptyList() : List.copyOf(trustedPrivateEndpoints);
        this.restrictedIpPatterns = restrictedIpPatterns == null ? Collections.emptyList() : List.copyOf(restrictedIpPatterns);
    }
}
