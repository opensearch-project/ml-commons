/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Logs connector URLs rejected by {@link Connector#validateResolvedEndpoint} in a form that is safe to write to a log. */
final class RejectedConnectorUrlLog {
    static final int MAX_LOGGED_LENGTH = 512;
    private static final Logger LOGGER = LogManager.getLogger(Connector.class);

    private RejectedConnectorUrlLog() {}

    static void warn(String resolvedUrl) {
        LOGGER.warn("Rejected connector URL: {}", sanitize(resolvedUrl));
    }

    /** Drops query and fragment, replaces control characters and caps the length. */
    static String sanitize(String resolvedUrl) {
        int cut = resolvedUrl.length();
        int query = resolvedUrl.indexOf('?');
        int fragment = resolvedUrl.indexOf('#');
        if (query >= 0) {
            cut = Math.min(cut, query);
        }
        if (fragment >= 0) {
            cut = Math.min(cut, fragment);
        }
        cut = Math.min(cut, MAX_LOGGED_LENGTH);
        StringBuilder sanitized = new StringBuilder(cut);
        for (int i = 0; i < cut; i++) {
            char c = resolvedUrl.charAt(i);
            sanitized.append(c < 0x20 || c == 0x7f ? '?' : c);
        }
        return sanitized.toString();
    }
}
