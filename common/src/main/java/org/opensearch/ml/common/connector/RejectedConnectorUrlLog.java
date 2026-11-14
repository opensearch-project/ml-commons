/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs connector URLs rejected by {@link Connector#validateResolvedEndpoint}. Only the scheme, the host and the
 * length are written: user information, path, query and fragment can carry secrets.
 */
final class RejectedConnectorUrlLog {
    private static final Logger LOGGER = LogManager.getLogger(Connector.class);

    private RejectedConnectorUrlLog() {}

    static void warn(String resolvedUrl) {
        LOGGER.warn("Rejected connector URL: {}", describe(resolvedUrl));
    }

    /**
     * The reason and position of a URI syntax error, without the input: {@link URISyntaxException#getMessage()} repeats
     * the whole URL.
     */
    static String describeSyntaxError(URISyntaxException e) {
        return e.getIndex() >= 0 ? e.getReason() + " at index " + e.getIndex() : e.getReason();
    }

    /** "scheme://host (length N)" when the URL parses with a scheme and host, otherwise "unparseable (length N)". */
    static String describe(String resolvedUrl) {
        if (resolvedUrl == null) {
            return "null";
        }
        String length = " (length " + resolvedUrl.length() + ")";
        try {
            URI uri = new URI(resolvedUrl);
            if (uri.getScheme() != null && uri.getHost() != null) {
                return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT) + length;
            }
        } catch (URISyntaxException e) {
            // fall through
        }
        return "unparseable" + length;
    }
}
