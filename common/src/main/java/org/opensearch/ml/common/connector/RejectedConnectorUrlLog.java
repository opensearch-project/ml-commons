/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Logs connector URLs rejected by {@link Connector#validateResolvedEndpoint}. Only the scheme, the host and the
 * length are written: user information, path, query and fragment can carry secrets.
 */
final class RejectedConnectorUrlLog {
    private static final Logger LOGGER = LogManager.getLogger(Connector.class);

    /**
     * Connector name and action pairs this node has already warned about for an unpinned origin. Bounded so that a churn of
     * connector names cannot grow it without limit; once full it starts over and each connector warns once more.
     */
    private static final int UNPINNED_ORIGIN_WARNED_LIMIT = 1_024;
    private static final Set<String> UNPINNED_ORIGIN_WARNED = ConcurrentHashMap.newKeySet();

    private RejectedConnectorUrlLog() {}

    static void warn(String resolvedUrl) {
        LOGGER.warn("Rejected connector URL: {}", describe(resolvedUrl));
    }

    /**
     * A request on a connector whose parameters give no origin for the action, allowed under the deprecated setting. Written
     * once per connector name and action on this node, not on every request: a connector behind an ingest pipeline would
     * otherwise repeat it for each document.
     */
    static void warnUnpinnedOrigin(String connectorName, String action, String origin) {
        if (!shouldWarnUnpinnedOrigin(connectorName, action)) {
            return;
        }
        LOGGER
            .warn(
                "Connector [{}] action [{}] stores no scheme or host for its URL; the request to {} passed the trusted endpoint patterns only. Store the parameters that give the host on the connector: this allowance is deprecated and ends in 4.0.",
                connectorName,
                action,
                origin
            );
    }

    /** True the first time a connector name and action pair is seen since the set was last cleared, false on a repeat. */
    static boolean shouldWarnUnpinnedOrigin(String connectorName, String action) {
        boolean first = UNPINNED_ORIGIN_WARNED.add(connectorName + "|" + action);
        if (UNPINNED_ORIGIN_WARNED.size() > UNPINNED_ORIGIN_WARNED_LIMIT) {
            UNPINNED_ORIGIN_WARNED.clear();
        }
        return first;
    }

    /** Forgets which connectors have been warned about, so tests do not depend on one another. */
    static void resetUnpinnedOriginWarnings() {
        UNPINNED_ORIGIN_WARNED.clear();
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
