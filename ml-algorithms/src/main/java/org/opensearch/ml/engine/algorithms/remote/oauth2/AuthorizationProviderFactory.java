/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;

/** Builds the {@link AuthorizationProvider} of a connector executor over the node's token cache and token client. */
public class AuthorizationProviderFactory {
    private final OAuth2TokenCache cache;
    private final OAuth2TokenClient tokenClient;
    private final MLFeatureEnabledSetting settings;

    public AuthorizationProviderFactory(OAuth2TokenCache cache, OAuth2TokenClient tokenClient, MLFeatureEnabledSetting settings) {
        this.cache = cache;
        this.tokenClient = tokenClient;
        this.settings = settings;
    }

    /**
     * The provider for a connector whose effective {@code auth_config} has a type that attaches a token. Reads only the
     * connector, never request input; does no I/O. The configuration must still pass the write checks under the current
     * trusted token endpoints. A {@code google_cloud} connector is served under its own settings
     * ({@link GoogleLegacyAuthorization}); every other connector under {@code plugins.ml_commons.connector.auth_enabled}.
     *
     * @throws IllegalArgumentException when the configuration no longer passes the write checks
     */
    public AuthorizationProvider providerFor(Connector connector) {
        OAuth2AuthConfig config = connector.getEffectiveAuthConfig();
        if (connector instanceof GoogleCloudConnector google) {
            return GoogleLegacyAuthorization.providerFor(google, config, this::oauth2Provider, settings);
        }
        return oauth2Provider(connector, config, connectorAuthGate(settings));
    }

    /** The gate of {@code plugins.ml_commons.connector.auth_enabled}. */
    static Gate connectorAuthGate(MLFeatureEnabledSetting settings) {
        return Gate.of(settings::isConnectorAuthEnabled, ML_COMMONS_CONNECTOR_AUTH_DISABLED_MESSAGE);
    }

    /** The provider of an oauth2 configuration over the node's cache and token client, every request subject to {@code gate}. */
    AuthorizationProvider oauth2Provider(Connector connector, OAuth2AuthConfig config, Gate gate) {
        Map<String, String> credential = connector.getDecryptedCredential();
        AuthConfigValidator.validate(connector, config, AuthConfigValidator.keysOf(credential), settings.getTrustedOAuth2TokenEndpoints());
        // Taken now: some callers clear the connector's decrypted credential when their action completes.
        CredentialSnapshot snapshot = CredentialSnapshot.of(credential, config.referencedCredentialKeys());
        CredentialResolver credentials = listener -> listener.onResponse(snapshot);
        // Sent under the gate the cache gives the request, that of the requests waiting for it, rather than this
        // provider's: a request of another connector with the same configuration may have joined it.
        OAuth2TokenCache.TokenSource source = (values, allowlist, requestGate, listener) -> tokenClient
            .requestToken(config, values, allowlist, requestGate, listener);
        String tenantId = connector.getTenantId();
        // The configuration as it is written: one cache entry per configuration, whichever connectors hold it.
        String configKey = Strings.toString(MediaTypeRegistry.JSON, config);
        return new AuthorizationProvider() {
            @Override
            public void authorizationValue(ActionListener<TokenHandle> listener) {
                cache.get(tenantId, configKey, credentials, source, gate, listener);
            }

            @Override
            public boolean invalidate(TokenHandle handle) {
                return cache.invalidate(handle);
            }
        };
    }
}
