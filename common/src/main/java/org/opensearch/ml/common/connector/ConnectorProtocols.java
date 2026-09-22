/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConnectorProtocols {

    public static final String HTTP = "http";
    public static final String AWS_SIGV4 = "aws_sigv4";
    public static final String GOOGLE_CLOUD = "google_cloud";
    public static final String MCP_SSE = "mcp_sse";
    public static final String MCP_STREAMABLE_HTTP = "mcp_streamable_http";

    public static final List<String> VALID_PROTOCOLS = Arrays.asList(AWS_SIGV4, HTTP, GOOGLE_CLOUD, MCP_SSE, MCP_STREAMABLE_HTTP);

    private static final Set<String> MCP_PROTOCOLS = Set.of(MCP_SSE, MCP_STREAMABLE_HTTP);

    /**
     * Whether the protocol speaks MCP. MCP connectors are a different shape from the inference protocols:
     * they carry no actions, so the action accessors on {@link Connector} are unimplemented for them and
     * callers that need an action have to check this first rather than discover it as a runtime failure.
     * <p>
     * Matching ignores case, unlike {@link #validateProtocol(String)} and the other protocol comparisons.
     * That is deliberate: this is the behaviour the header validation in {@code AbstractConnector} already
     * had, and every caller uses it to <em>refuse</em> something, so recognising more spellings can only
     * fail closed. It is not a licence to accept a mixed-case protocol anywhere else - {@code validateProtocol}
     * still rejects those, which is why type and protocol can never disagree on a parsed connector.
     *
     * @param protocol connector protocol, may be null
     * @return true if the protocol is one of the MCP protocols
     */
    public static boolean isMcpProtocol(String protocol) {
        return protocol != null && MCP_PROTOCOLS.contains(protocol.toLowerCase(Locale.ROOT));
    }

    public static void validateProtocol(String protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("Connector protocol is null. Please use one of " + supportedProtocols());
        }
        if (!VALID_PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("Unsupported connector protocol. Please use one of " + supportedProtocols());
        }
    }

    public static String supportedProtocols() {
        return "[" + String.join(", ", VALID_PROTOCOLS) + "]";
    }
}
