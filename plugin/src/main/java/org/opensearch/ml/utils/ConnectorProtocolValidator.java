/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;

import java.util.Set;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;

/**
 * Checks a connector protocol against the opt-in feature flag that guards it.
 * <p>
 * Some protocols are only usable when an operator has explicitly enabled them. Those checks have to be applied
 * wherever a connector's protocol is <em>established or changed</em>, not only where it is first created —
 * otherwise the flag can be sidestepped by creating a connector with an allowed protocol and then switching it,
 * or by supplying an inline connector on a model request. Call
 * {@link #validateProtocolEnabled(String, MLFeatureEnabledSetting)} with the protocol the connector will have
 * <em>after</em> the request is applied.
 */
public class ConnectorProtocolValidator {

    private ConnectorProtocolValidator() {}

    /**
     * @param protocol the resulting connector protocol; {@code null} is ignored, since a request that does not
     *                 set a protocol cannot move the connector into a gated one
     * @param mlFeatureEnabledSetting feature flag accessor
     * @throws OpenSearchStatusException with {@link RestStatus#FORBIDDEN} if the protocol is gated off
     */
    public static void validateProtocolEnabled(String protocol, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (protocol == null) {
            return;
        }
        boolean isMcpProtocol = ConnectorProtocols.MCP_SSE.equals(protocol) || ConnectorProtocols.MCP_STREAMABLE_HTTP.equals(protocol);
        if (isMcpProtocol && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        if (ConnectorProtocols.GOOGLE_CLOUD.equals(protocol) && !mlFeatureEnabledSetting.isVertexAIConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
    }

    /**
     * Protocols whose executors build their HTTP client without the mutual-TLS material. Accepting
     * {@code mutual_tls_enabled} on these reports a transport protection that is never actually applied.
     */
    private static final Set<String> PROTOCOLS_WITHOUT_MUTUAL_TLS = Set
        .of(
            ConnectorProtocols.AWS_SIGV4,
            ConnectorProtocols.GOOGLE_CLOUD,
            ConnectorProtocols.MCP_SSE,
            ConnectorProtocols.MCP_STREAMABLE_HTTP
        );

    /**
     * Rejects {@code mutual_tls_enabled} on protocols that do not implement it.
     * <p>
     * The field is accepted on every protocol but only honoured by the non-streaming {@code http} executor.
     * On the others the connector is created, reported back with {@code mutual_tls_enabled: true}, and then
     * connects without a client certificate - so an operator believes a control is in force that is not.
     * Failing the request is the only way they find out.
     *
     * @param protocol the resulting connector protocol
     * @param clientConfig the resulting connector client config; {@code null} or mTLS-off is ignored
     * @throws IllegalArgumentException if mTLS is requested on a protocol that cannot apply it
     */
    public static void validateMutualTlsSupported(String protocol, ConnectorClientConfig clientConfig) {
        if (!requestsUnsupportedMutualTls(protocol, clientConfig)) {
            return;
        }
        throw new IllegalArgumentException(
            "Mutual TLS is not supported for connector protocol ["
                + protocol
                + "]. "
                + ConnectorClientConfig.MUTUAL_TLS_ENABLED_FIELD
                + " is only applied to the non-streaming ["
                + ConnectorProtocols.HTTP
                + "] protocol, so enabling it here would have no effect."
        );
    }

    /**
     * Update variant of {@link #validateMutualTlsSupported(String, ConnectorClientConfig)}.
     * <p>
     * An update merges protocol and client config independently, so checking only the values the request
     * carries misses the case where switching the protocol alone leaves an already-stored
     * {@code mutual_tls_enabled} stranded on a protocol that cannot apply it. This validates the combination
     * the connector will actually end up with.
     * <p>
     * It stays quiet when the stored connector was <em>already</em> in that unsupported state, so an unrelated
     * edit to a connector created before this check existed is not blocked. Only newly created bad
     * combinations are rejected.
     */
    public static void validateMutualTlsSupportedAfterUpdate(
        String storedProtocol,
        ConnectorClientConfig storedConfig,
        String updatedProtocol,
        ConnectorClientConfig updatedConfig
    ) {
        if (requestsUnsupportedMutualTls(storedProtocol, storedConfig)) {
            return;
        }
        validateMutualTlsSupported(
            updatedProtocol != null ? updatedProtocol : storedProtocol,
            updatedConfig != null ? updatedConfig : storedConfig
        );
    }

    private static boolean requestsUnsupportedMutualTls(String protocol, ConnectorClientConfig clientConfig) {
        if (protocol == null || clientConfig == null || !Boolean.TRUE.equals(clientConfig.getMutualTlsEnabled())) {
            return false;
        }
        return PROTOCOLS_WITHOUT_MUTUAL_TLS.contains(protocol);
    }
}
