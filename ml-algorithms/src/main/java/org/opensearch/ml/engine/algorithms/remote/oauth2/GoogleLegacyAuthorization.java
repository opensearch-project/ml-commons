/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.GoogleCloudConnector;
import org.opensearch.ml.common.connector.auth.AuthConfigValidator;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;
import org.opensearch.ml.common.settings.MLFeatureEnabledSetting;

/**
 * The authorization of a {@code google_cloud} connector. A legacy connector, which stores no {@code auth_config}, runs
 * under {@code plugins.ml_commons.connector.vertexai_enabled} alone, read live before every token request and hand-out:
 * its service account through the shared token engine, its application default credentials through the Google auth
 * library. A connector with an explicit {@code auth_config} runs under that setting and
 * {@code plugins.ml_commons.connector.auth_enabled}, as the write checks required both.
 */
final class GoogleLegacyAuthorization {

    private static final Logger log = LogManager.getLogger(GoogleLegacyAuthorization.class);

    /** Builds the provider of an oauth2 configuration over the node's token engine. */
    @FunctionalInterface
    interface Oauth2ProviderBuilder {
        AuthorizationProvider build(Connector connector, OAuth2AuthConfig config, Gate gate);
    }

    private GoogleLegacyAuthorization() {}

    /**
     * @param config the connector's effective configuration: the explicit block, or the one derived from the legacy identity
     * @throws IllegalArgumentException when the configuration no longer passes the write checks
     */
    static AuthorizationProvider providerFor(
        GoogleCloudConnector connector,
        OAuth2AuthConfig config,
        Oauth2ProviderBuilder oauth2Provider,
        MLFeatureEnabledSetting settings
    ) {
        Gate vertexAi = Gate.of(settings::isVertexAIConnectorEnabled, ML_COMMONS_VERTEXAI_CONNECTOR_DISABLED_MESSAGE);
        AuthorizationProvider provider;
        if (config.getType() == ConnectorAuthType.GOOGLE_ADC) {
            // The library makes the token request; the connector-level checks of an active configuration still apply.
            AuthConfigValidator
                .validate(
                    connector,
                    config,
                    AuthConfigValidator.keysOf(connector.getDecryptedCredential()),
                    settings.getTrustedOAuth2TokenEndpoints(),
                    settings.getOAuth2ExtensionGrants()
                );
            provider = new GoogleAdcAuthorizationProvider(List.of(connector.getScopes()), vertexAi);
        } else {
            Gate gate = connector.getAuthConfig() == null
                ? vertexAi
                : Gate.both(vertexAi, AuthorizationProviderFactory.connectorAuthGate(settings));
            provider = oauth2Provider.build(connector, config, gate);
        }
        if (connector.getAuthConfig() == null && AuthConfigValidator.definesAuthorizationHeader(connector)) {
            // Written before such a header was refused; the token replaces it on every request. Once per provider.
            log
                .warn(
                    "google_cloud connector [{}] defines an Authorization header, which its access token replaces; remove the header from the connector",
                    connector.getName()
                );
        }
        return provider;
    }
}
