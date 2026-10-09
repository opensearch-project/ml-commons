/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_MUTUAL_TLS_DISABLED_MESSAGE;
import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.connector.ConnectorClientConfig;
import org.opensearch.ml.common.connector.ConnectorProtocols;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

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
        if (ConnectorProtocols.isMcpProtocol(protocol) && !mlFeatureEnabledSetting.isMcpConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
        if (ConnectorProtocols.GOOGLE_CLOUD.equals(protocol) && !mlFeatureEnabledSetting.isVertexAIConnectorEnabled()) {
            throw new OpenSearchStatusException(ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE, RestStatus.FORBIDDEN);
        }
    }

    /**
     * Rejects a connector whose protocol does not accept the fields the connector actually carries.
     * <p>
     * A protocol is not only a label: it selects the class a stored connector document is parsed back into, and
     * each class enforces the fields it cannot work without - {@code aws_sigv4} a signing credential,
     * {@code google_cloud} service-account material. Nothing checks that pairing once a connector exists, so a
     * connector can be relabelled onto a protocol whose class rejects the document it has become. The document is
     * then written, and every later read of it throws: the resource is unreadable, unrepairable, and - because
     * delete parses the document too - not removable through the public API either.
     * <p>
     * The check is the read-back path itself rather than a restatement of each class's rules. The connector is
     * serialised and handed to {@link Connector#createConnector(XContentBuilder, String)} the way the index hands
     * it back, so anything that would leave the stored document unparseable fails here first, and a protocol added
     * later is covered without this class being told about it.
     *
     * @param connector the resulting connector
     * @throws OpenSearchStatusException with {@link RestStatus#BAD_REQUEST} if the resulting protocol's connector
     *         class rejects the resulting connector
     */
    public static void validateProtocolRequirements(Connector connector) {
        String reason;
        try (XContentBuilder builder = XContentFactory.jsonBuilder()) {
            connector.toXContent(builder, ToXContent.EMPTY_PARAMS);
            if (Connector.createConnector(builder, connector.getProtocol()) != null) {
                return;
            }
            // createConnector reports anything that is not an IllegalArgumentException by returning null.
            reason = "it cannot be parsed as that protocol's connector";
        } catch (IllegalArgumentException | IOException e) {
            reason = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        throw new OpenSearchStatusException(
            "Connector protocol ["
                + connector.getProtocol()
                + "] does not accept the connector this request would produce: "
                + reason
                + ". Supply the fields that protocol requires in the same request.",
            RestStatus.BAD_REQUEST
        );
    }

    /**
     * Update variant of {@link #validateProtocolRequirements(Connector)}.
     * <p>
     * The merged state is produced by applying the request to a copy of the stored connector through
     * {@code Connector#update} itself, rather than by merging the fields again here. That merge is uneven - the
     * protocol, credential and actions are replaced while the parameters are merged - and a second copy of those
     * rules is the thing that would drift away from the one that decides what gets written.
     * <p>
     * There is no grandfathering clause, unlike the other update variants here, because there is nothing to
     * grandfather: a connector already in this state does not parse, so neither update path can load one to begin
     * with. Every rejection this adds is a document the request itself would have broken.
     *
     * @param storedConnector the connector as it is now
     * @param updateContent the update the request carries
     */
    public static void validateProtocolRequirementsAfterUpdate(Connector storedConnector, MLCreateConnectorInput updateContent) {
        Connector mergedConnector = storedConnector.cloneConnector();
        mergedConnector.update(updateContent);
        validateProtocolRequirements(mergedConnector);
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

    /** A literal cleartext scheme at the start of an action URL. */
    private static final Pattern CLEARTEXT_URL = Pattern.compile("^\\s*http://", Pattern.CASE_INSENSITIVE);

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
        if (actions == null || clientConfig == null || !Boolean.TRUE.equals(clientConfig.getMutualTlsEnabled())) {
            return null;
        }
        StringSubstitutor substitutor = new StringSubstitutor(parameters == null ? Map.of() : parameters, "${parameters.", "}");
        for (ConnectorAction action : actions) {
            String url = action == null ? null : action.getUrl();
            if (url == null) {
                continue;
            }
            String resolved = substitutor.replace(url);
            if (CLEARTEXT_URL.matcher(resolved).find()) {
                return resolved.trim();
            }
        }
        return null;
    }
}
