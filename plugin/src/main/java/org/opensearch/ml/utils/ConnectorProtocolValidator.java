/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.connector.HttpConnector.MINIMAL_SUPPORTED_VERSION_FOR_CONNECTOR_AUTH;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MUTUAL_TLS_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.connector.auth.CredentialReference;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
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
 * <p>
 * The class also holds the write-time checks of a connector's {@code auth_config}.
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
        if (ConnectorProtocols.isMcpProtocol(protocol) && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        if (ConnectorProtocols.GOOGLE_CLOUD.equals(protocol) && !mlFeatureEnabledSetting.isVertexAIConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
    }

    /**
     * Rejects a request that turns {@code mutual_tls_enabled} on while the mutual-TLS setting is off.
     * <p>
     * Create variant: there is no stored connector, so any request asking for mutual TLS is turning it on.
     * <p>
     * Call this <em>after</em> {@link #validateMutualTlsSupported(String, ConnectorClientConfig)}: when a
     * request asks for mutual TLS on a protocol that can never apply it <em>and</em> the setting is off, the
     * protocol message is the useful one, because enabling the setting would not make that request work.
     *
     * @param clientConfig the resulting connector client config; {@code null} or mTLS-off is ignored
     * @param mlFeatureEnabledSetting feature flag accessor
     * @throws OpenSearchStatusException with {@link RestStatus#FORBIDDEN} if mutual TLS is being turned on
     *         while the setting is off
     */
    public static void validateMutualTlsEnabled(ConnectorClientConfig clientConfig, MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        validateMutualTlsEnabledAfterUpdate(null, clientConfig, mlFeatureEnabledSetting);
    }

    /**
     * Update variant of {@link #validateMutualTlsEnabled(ConnectorClientConfig, MLFeatureEnabledSetting)}.
     * <p>
     * A connector whose stored config already has mutual TLS on is left alone. That is not laxness: an update
     * replaces {@code client_config} wholesale ({@code HttpConnector#update}), so an operator editing an
     * unrelated field such as {@code max_connection} has to re-send {@code mutual_tls_enabled} to avoid
     * dropping it. Rejecting that would make an existing mutual-TLS connector uneditable the moment the
     * setting is turned off, rather than merely preventing new reliance on the control.
     *
     * @param storedConfig the client config the connector has now; {@code null} for a create
     * @param updatedConfig the client config the request carries
     * @param mlFeatureEnabledSetting feature flag accessor
     */
    public static void validateMutualTlsEnabledAfterUpdate(
        ConnectorClientConfig storedConfig,
        ConnectorClientConfig updatedConfig,
        MLFeatureEnabledSetting mlFeatureEnabledSetting
    ) {
        if (updatedConfig == null || !Boolean.TRUE.equals(updatedConfig.getMutualTlsEnabled())) {
            return;
        }
        if (storedConfig != null && Boolean.TRUE.equals(storedConfig.getMutualTlsEnabled())) {
            return;
        }
        if (!mlFeatureEnabledSetting.isMutualTlsEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_MUTUAL_TLS_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
    }

    /**
     * Protocols whose executors build their HTTP client without the mutual-TLS material. Accepting
     * {@code mutual_tls_enabled} on these reports a transport protection that is never actually applied.
     * <p>
     * The MCP protocols are not listed: their executors install the client certificate on the JDK HTTP
     * client through {@code MLSslContextFactory}.
     */
    private static final Set<String> PROTOCOLS_WITHOUT_MUTUAL_TLS = Set.of(ConnectorProtocols.AWS_SIGV4, ConnectorProtocols.GOOGLE_CLOUD);

    /**
     * Rejects {@code mutual_tls_enabled} on protocols that do not implement it.
     * <p>
     * The field is accepted on every protocol but only honoured by the non-streaming {@code http} executor and
     * the MCP executors. On the others the connector is created, reported back with
     * {@code mutual_tls_enabled: true}, and then connects without a client certificate - so an operator believes
     * a control is in force that is not. Failing the request is the only way they find out.
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
                + "] protocol and the MCP protocols, so enabling it here would have no effect."
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
     * It stays quiet when the stored connector was <em>already</em> in that unsupported state <em>and</em> the
     * request leaves its protocol alone, so an unrelated edit to a connector created before this check existed
     * is not blocked. Grandfathering does not extend to moving such a connector onto a <em>different</em>
     * unsupported protocol: that is a state the request itself creates, and permitting it would let the
     * misleading {@code mutual_tls_enabled} be carried around indefinitely.
     */
    public static void validateMutualTlsSupportedAfterUpdate(
        String storedProtocol,
        ConnectorClientConfig storedConfig,
        String updatedProtocol,
        ConnectorClientConfig updatedConfig
    ) {
        boolean protocolUnchanged = updatedProtocol == null || updatedProtocol.equals(storedProtocol);
        if (protocolUnchanged && requestsUnsupportedMutualTls(storedProtocol, storedConfig)) {
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

    /**
     * Rejects {@code mutual_tls_enabled} on a connector whose action URL is plain {@code http://}.
     * <p>
     * Certificate validation never sees a URL, and the request path permits both schemes explicitly, so against
     * an {@code http://} endpoint no TLS handshake happens at all: the key managers built from the client
     * certificate are inert, nothing is presented to the server, and the traffic is cleartext. The connector
     * still reports {@code mutual_tls_enabled: true}, so an operator believes a control is in force that is not.
     * <p>
     * The connector's own {@code parameters} are substituted into the URL first, the same way
     * {@link Connector#validateConnectorURL(List)} resolves it before testing it against the trusted-endpoint
     * allowlist. A URL written as <code>${parameters.endpoint}/predict</code> therefore gets judged whenever the
     * connector supplies {@code endpoint} itself. Only a URL whose scheme is <em>still</em> unresolved after
     * substitution is left alone, since that value genuinely does not exist until predict time.
     * <p>
     * On a stock cluster this combination is already refused elsewhere - every default
     * {@code trusted_connector_endpoints_regex} pattern is anchored {@code ^https://}. This check is what covers
     * a deployment that has widened that allowlist, where the endpoint check no longer says anything about the
     * scheme.
     *
     * @param actions the resulting connector actions; {@code null} is ignored
     * @param parameters the resulting connector parameters, substituted into the URLs; may be {@code null}
     * @param clientConfig the resulting connector client config; {@code null} or mTLS-off is ignored
     * @throws IllegalArgumentException if mutual TLS is requested alongside a cleartext action URL
     */
    public static void validateMutualTlsScheme(
        List<ConnectorAction> actions,
        Map<String, String> parameters,
        ConnectorClientConfig clientConfig
    ) {
        String cleartextUrl = cleartextActionUrl(actions, parameters, clientConfig);
        if (cleartextUrl == null) {
            return;
        }
        throw new IllegalArgumentException(
            "Mutual TLS cannot be used with the cleartext endpoint ["
                + cleartextUrl
                + "]. "
                + ConnectorClientConfig.MUTUAL_TLS_ENABLED_FIELD
                + " requires an https:// action URL: over http:// there is no TLS handshake, so the client "
                + "certificate is never presented and the request is not encrypted."
        );
    }

    /**
     * Update variant of {@link #validateMutualTlsScheme(List, Map, ConnectorClientConfig)}.
     * <p>
     * An update replaces the action list wholesale when it carries one ({@code HttpConnector#update}), so the
     * resulting URLs are the request's when it supplies actions and the stored ones otherwise. Parameters are
     * different: {@code HttpConnector#update} merges them with {@code putAll}, so the map to substitute is the
     * stored one with the request's entries applied over it, not one or the other.
     * <p>
     * A connector that is <em>already</em> in this state and whose URLs the request leaves alone is not blocked,
     * for the same reason as the other update variants here: an unrelated edit has to re-send
     * {@code client_config} to avoid dropping {@code mutual_tls_enabled}, and rejecting that would make such a
     * connector uneditable rather than merely preventing new reliance on the control. A request that introduces
     * the cleartext URL itself is still rejected.
     *
     * @param storedActions the actions the connector has now; {@code null} for a create
     * @param storedParameters the parameters the connector has now; {@code null} for a create
     * @param storedConfig the client config the connector has now; {@code null} for a create
     * @param updatedActions the actions the request carries
     * @param updatedParameters the parameters the request carries
     * @param updatedConfig the client config the request carries
     */
    public static void validateMutualTlsSchemeAfterUpdate(
        List<ConnectorAction> storedActions,
        Map<String, String> storedParameters,
        ConnectorClientConfig storedConfig,
        List<ConnectorAction> updatedActions,
        Map<String, String> updatedParameters,
        ConnectorClientConfig updatedConfig
    ) {
        Map<String, String> mergedParameters = mergeParameters(storedParameters, updatedParameters);
        if (updatedActions == null
            && updatedParameters == null
            && cleartextActionUrl(storedActions, storedParameters, storedConfig) != null) {
            return;
        }
        validateMutualTlsScheme(
            updatedActions != null ? updatedActions : storedActions,
            mergedParameters,
            updatedConfig != null ? updatedConfig : storedConfig
        );
    }

    /**
     * MCP counterpart of {@link #validateMutualTlsScheme(List, Map, ConnectorClientConfig)}.
     * <p>
     * An MCP connector has no actions: its endpoint is the top-level {@code url}, which the action-based check
     * never sees. Against an {@code http://} MCP server the client certificate is never presented, exactly as
     * for an action URL, so the same rejection applies.
     *
     * @param protocol the resulting connector protocol; non-MCP protocols are ignored
     * @param url the resulting connector url
     * @param clientConfig the resulting connector client config; {@code null} or mTLS-off is ignored
     * @throws IllegalArgumentException if mutual TLS is requested alongside a cleartext MCP url
     */
    public static void validateMcpMutualTlsScheme(String protocol, String url, ConnectorClientConfig clientConfig) {
        if (!isCleartextMcpUrl(protocol, url, clientConfig)) {
            return;
        }
        throw new IllegalArgumentException(
            "Mutual TLS cannot be used with the cleartext endpoint ["
                + url.trim()
                + "]. "
                + ConnectorClientConfig.MUTUAL_TLS_ENABLED_FIELD
                + " requires an https:// MCP server url: over http:// there is no TLS handshake, so the client "
                + "certificate is never presented and the request is not encrypted."
        );
    }

    /**
     * Update variant of {@link #validateMcpMutualTlsScheme(String, String, ConnectorClientConfig)}, validating
     * the url and client config the connector will have once the request is applied. As with the other update
     * variants, a connector already in this state whose url the request leaves alone is not blocked.
     */
    public static void validateMcpMutualTlsSchemeAfterUpdate(
        String protocol,
        String storedUrl,
        ConnectorClientConfig storedConfig,
        String updatedUrl,
        ConnectorClientConfig updatedConfig
    ) {
        if (updatedUrl == null && isCleartextMcpUrl(protocol, storedUrl, storedConfig)) {
            return;
        }
        validateMcpMutualTlsScheme(
            protocol,
            updatedUrl != null ? updatedUrl : storedUrl,
            updatedConfig != null ? updatedConfig : storedConfig
        );
    }

    private static boolean isCleartextMcpUrl(String protocol, String url, ConnectorClientConfig clientConfig) {
        return ConnectorProtocols.isMcpProtocol(protocol)
            && url != null
            && clientConfig != null
            && Boolean.TRUE.equals(clientConfig.getMutualTlsEnabled())
            && ConnectorAction.CLEARTEXT_URL.matcher(url).find();
    }

    /** Mirrors {@code HttpConnector#update}, which merges parameters with putAll rather than replacing them. */
    private static Map<String, String> mergeParameters(Map<String, String> stored, Map<String, String> updated) {
        if (stored == null) {
            return updated;
        }
        if (updated == null) {
            return stored;
        }
        Map<String, String> merged = new HashMap<>(stored);
        merged.putAll(updated);
        return merged;
    }

    /**
     * @return the first action URL that resolves to cleartext while mutual TLS is on, or {@code null} if there
     *         is none - including when mutual TLS is off, in which case the scheme is not this check's business
     */
    private static String cleartextActionUrl(
        List<ConnectorAction> actions,
        Map<String, String> parameters,
        ConnectorClientConfig clientConfig
    ) {
        if (clientConfig == null || !Boolean.TRUE.equals(clientConfig.getMutualTlsEnabled())) {
            return null;
        }
        return ConnectorAction.firstCleartextUrl(actions, parameters);
    }

    // ---- auth_config --------------------------------------------------------------------------------------------

    /** What {@link #firstChangedProtectedField} reports when the set of referenced credential keys differs. */
    static final String REFERENCED_KEYS = "credential keys referenced by auth_config";

    /** A request that carries {@code auth_config} without a credential changes the referenced credential keys. */
    public static final String REFERENCED_KEYS_CREDENTIAL_REQUIRED_MESSAGE =
        "Changing the credential keys referenced by auth_config requires credential in the same update";
    /** A caller-supplied connector drops a field of the {@code auth_config} the stored connector has. */
    public static final String SUPPLIED_CONNECTOR_REMOVES_FIELDS_MESSAGE =
        "A caller-supplied updatedConnector cannot remove auth_config fields; update the model's connector instead";
    /** A caller-supplied connector changes the referenced credential keys. */
    public static final String SUPPLIED_CONNECTOR_CHANGES_REFERENCED_KEYS_MESSAGE =
        "A caller-supplied updatedConnector cannot change the credential keys referenced by auth_config; update the model's connector instead";

    /** The message for a configuration on a protocol that does not take {@code auth_config}. */
    public static String protocolNotSupportedMessage(String protocol) {
        return "auth_config is not supported for connector protocol [" + protocol + "]";
    }

    /** The message for a configuration written while {@code oldest}, the oldest node of the cluster, predates the feature. */
    public static String versionGuardMessage(Version oldest) {
        return "Writing auth_config requires every node to run "
            + MINIMAL_SUPPORTED_VERSION_FOR_CONNECTOR_AUTH
            + " or later; the oldest node runs "
            + oldest;
    }

    /** The message for a request that carries {@code auth_config} without a credential and changes protected {@code field}. */
    public static String credentialRequiredMessage(String field) {
        return "Changing auth_config." + field + " requires credential in the same update";
    }

    /** The message for a caller-supplied connector that changes protected {@code field}. */
    public static String suppliedConnectorChangesMessage(String field) {
        return "A caller-supplied updatedConnector cannot change auth_config." + field + "; update the model's connector instead";
    }

    /**
     * Write-time checks of the {@code auth_config} of a connector that is created, registered inline, or supplied whole
     * by a transport caller. In order: the protocol takes {@code auth_config}, every node of the cluster supports it,
     * and the feature flag is on for a type that attaches credentials.
     * <p>
     * A request without {@code auth_config} is not judged, and neither the flag nor the cluster state is read. A
     * configuration without a protocol is rejected like one on a protocol that does not take {@code auth_config};
     * every request parser that leads to a write already rejects a missing protocol, so only a dry run or a connector
     * built in code can reach here without one.
     *
     * @param protocol the connector protocol
     * @param authConfig the configuration the request carries; may be null
     * @param mlFeatureEnabledSetting feature flag accessor
     * @param clusterState the current cluster state; read only for a configuration
     * @throws IllegalArgumentException when the configuration is not accepted
     * @throws OpenSearchStatusException with {@link RestStatus#FORBIDDEN} for a type that attaches credentials while
     *     {@code plugins.ml_commons.connector.auth_enabled} is off
     */
    public static void validateAuth(
        String protocol,
        OAuth2AuthConfig authConfig,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Supplier<ClusterState> clusterState
    ) {
        if (authConfig == null) {
            return;
        }
        requireProtocolTakesAuthConfig(protocol);
        requireEveryNodeSupportsConnectorAuth(clusterState);
        if (authConfig.getType().isActive()) {
            requireConnectorAuthEnabled(mlFeatureEnabledSetting);
        }
    }

    /** Whether an update carries {@code auth_config} or changes the protocol; only such an update is judged. */
    public static boolean touchesAuthOrProtocol(OAuth2AuthConfig updated, String updatedProtocol, String storedProtocol) {
        return updated != null || (updatedProtocol != null && !updatedProtocol.equals(storedProtocol));
    }

    /**
     * Update variant of {@link #validateAuth}, for the connector API and a model's inline connector. It judges the
     * {@code auth_config} the connector will hold once {@code stored.update()} has applied the request, and must run
     * before that call, which modifies the stored connector in place.
     * <p>
     * A request that neither carries {@code auth_config} nor changes the protocol is not judged. The resulting
     * configuration is the request's when it carries one and the stored one otherwise; a connector that ends up
     * without one is not judged further. The checks then run in the {@link #validateAuth} order, followed by the
     * protected-field check: a request that carries {@code auth_config} without a credential may not change a
     * protected field or the referenced credential keys, or switch to a type that attaches credentials. The flag and
     * the protected-field check apply only to a request that carries {@code auth_config}; the version guard applies to
     * every request that is judged, an identical re-send included.
     *
     * @param stored the connector as fetched; never null
     * @param updatedProtocol the protocol the request sets, or null
     * @param updated the configuration the request carries, or null
     * @param updatedCredential the credential the request carries; null or empty means none
     * @param mlFeatureEnabledSetting feature flag accessor
     * @param clusterState the current cluster state; read only when a configuration is judged
     */
    public static void validateAuthAfterUpdate(
        Connector stored,
        String updatedProtocol,
        OAuth2AuthConfig updated,
        Map<String, String> updatedCredential,
        MLFeatureEnabledSetting mlFeatureEnabledSetting,
        Supplier<ClusterState> clusterState
    ) {
        boolean carriesAuth = updated != null;
        String storedProtocol = stored.getProtocol();
        if (!touchesAuthOrProtocol(updated, updatedProtocol, storedProtocol)) {
            return;
        }
        OAuth2AuthConfig resulting = carriesAuth ? updated : stored.getAuthConfig();
        if (resulting == null) {
            return;
        }
        requireProtocolTakesAuthConfig(updatedProtocol != null ? updatedProtocol : storedProtocol);
        requireEveryNodeSupportsConnectorAuth(clusterState);
        if (carriesAuth && resulting.getType().isActive()) {
            requireConnectorAuthEnabled(mlFeatureEnabledSetting);
        }
        if (carriesAuth && (updatedCredential == null || updatedCredential.isEmpty())) {
            Optional<String> changed = firstChangedProtectedField(stored.getAuthConfig(), resulting);
            if (changed.isPresent()) {
                throw new IllegalArgumentException(
                    REFERENCED_KEYS.equals(changed.get())
                        ? REFERENCED_KEYS_CREDENTIAL_REQUIRED_MESSAGE
                        : credentialRequiredMessage(changed.get())
                );
            }
        }
    }

    /**
     * Checks a connector a transport caller supplies whole for a model, against the model's stored inline connector.
     * Such a connector replaces the stored one and arrives without a plaintext credential, so it may not remove an
     * {@code auth_config} the stored connector has or any of its fields, and may not change a protected field or the
     * referenced credential keys. A stored connector without {@code auth_config} compares as {@code {"type": "none"}}.
     *
     * @param stored the model's stored inline connector; null for a model without one
     * @param supplied the caller-supplied connector
     * @throws IllegalArgumentException when the supplied connector is not accepted
     */
    public static void validateSuppliedConnector(Connector stored, Connector supplied) {
        OAuth2AuthConfig storedConfig = stored == null ? null : stored.getAuthConfig();
        OAuth2AuthConfig suppliedConfig = supplied.getAuthConfig();
        // The supplied object marks nothing as removed, so a dropped field would survive on a merging store.
        if (storedConfig != null && (suppliedConfig == null || suppliedConfig.dropsMembers(storedConfig))) {
            throw new IllegalArgumentException(SUPPLIED_CONNECTOR_REMOVES_FIELDS_MESSAGE);
        }
        Optional<String> changed = firstChangedProtectedField(storedConfig, suppliedConfig);
        if (changed.isPresent()) {
            throw new IllegalArgumentException(
                REFERENCED_KEYS.equals(changed.get())
                    ? SUPPLIED_CONNECTOR_CHANGES_REFERENCED_KEYS_MESSAGE
                    : suppliedConnectorChangesMessage(changed.get())
            );
        }
    }

    /**
     * The first protected field whose value differs between the stored configuration and the resulting one, then
     * {@link #REFERENCED_KEYS} when the set of referenced credential keys differs. Empty when nothing protected
     * changes, and when the result is absent or {@code none}. Not protected: {@code token_response.*}.
     * <p>
     * A stored side that is absent compares as {@code {"type": "none"}}. A literal {@code token_url} compares in its
     * canonical form, a reference as written.
     *
     * @param stored the stored configuration, possibly null
     * @param resulting the configuration the write leaves, possibly null
     * @return the name of the first changed field, or {@link #REFERENCED_KEYS}
     */
    static Optional<String> firstChangedProtectedField(OAuth2AuthConfig stored, OAuth2AuthConfig resulting) {
        if (resulting == null || !resulting.getType().isActive()) {
            // A none result holds no field and attaches nothing, so switching to none needs no credential.
            return Optional.empty();
        }
        OAuth2AuthConfig before = stored == null ? OAuth2AuthConfig.NONE : stored;
        if (!Objects.equals(comparableTokenUrl(before.getTokenUrl()), comparableTokenUrl(resulting.getTokenUrl()))) {
            return Optional.of(OAuth2AuthConfig.TOKEN_URL_FIELD);
        }
        if (!Objects.equals(before.getGrantType(), resulting.getGrantType())) {
            return Optional.of(OAuth2AuthConfig.GRANT_TYPE_FIELD);
        }
        if (!Objects.equals(before.getClientAuthMethod(), resulting.getClientAuthMethod())) {
            return Optional.of(OAuth2AuthConfig.CLIENT_AUTH_METHOD_FIELD);
        }
        if (!Objects.equals(before.getClientId(), resulting.getClientId())) {
            return Optional.of(OAuth2AuthConfig.CLIENT_ID_FIELD);
        }
        if (!Objects.equals(before.getScope(), resulting.getScope())) {
            return Optional.of(OAuth2AuthConfig.SCOPE_FIELD);
        }
        if (!Objects.equals(before.getAudience(), resulting.getAudience())) {
            return Optional.of(OAuth2AuthConfig.AUDIENCE_FIELD);
        }
        if (!before.getTokenRequestParams().equals(resulting.getTokenRequestParams())) {
            return Optional.of(OAuth2AuthConfig.TOKEN_REQUEST_PARAMS_FIELD);
        }
        if (!before.referencedCredentialKeys().equals(resulting.referencedCredentialKeys())) {
            return Optional.of(REFERENCED_KEYS);
        }
        return Optional.empty();
    }

    /** A literal {@code token_url} in its canonical form; a reference, or null, as it is. */
    private static String comparableTokenUrl(String tokenUrl) {
        return tokenUrl == null || CredentialReference.isReference(tokenUrl) ? tokenUrl : OAuth2AuthConfig.canonicalTokenUrl(tokenUrl);
    }

    private static void requireProtocolTakesAuthConfig(String protocol) {
        if (!ConnectorProtocols.supportsAuthConfig(protocol)) {
            throw new IllegalArgumentException(protocolNotSupportedMessage(protocol));
        }
    }

    /**
     * A node of an older release would ignore a stored {@code auth_config}, and its own writes would drop it on some
     * metadata stores, so no configuration is written while one is part of the cluster.
     */
    private static void requireEveryNodeSupportsConnectorAuth(Supplier<ClusterState> clusterState) {
        Version oldest = clusterState.get().nodes().getMinNodeVersion();
        if (oldest.before(MINIMAL_SUPPORTED_VERSION_FOR_CONNECTOR_AUTH)) {
            throw new IllegalArgumentException(versionGuardMessage(oldest));
        }
    }

    private static void requireConnectorAuthEnabled(MLFeatureEnabledSetting mlFeatureEnabledSetting) {
        if (!mlFeatureEnabledSetting.isConnectorAuthEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
    }
}
