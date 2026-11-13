/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.algorithms.remote.oauth2;

import java.util.Map;

import org.opensearch.core.action.ActionListener;
import org.opensearch.core.common.Strings;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.ml.common.connector.Connector;
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
     * The provider for a connector whose {@code auth_config} has a type that attaches a token. Reads only the connector,
     * never request input; does no I/O. The configuration must still pass the write checks under the current trusted
     * token endpoints.
     *
     * @throws IllegalArgumentException when the configuration no longer passes the write checks
     */
    public AuthorizationProvider providerFor(Connector connector) {
        Map<String, String> credential = connector.getDecryptedCredential();
        OAuth2AuthConfig config = AuthConfigValidator
            .validate(connector, AuthConfigValidator.keysOf(credential), settings.getTrustedOAuth2TokenEndpoints());
        // Taken now: some callers clear the connector's decrypted credential when their action completes.
        CredentialSnapshot snapshot = CredentialSnapshot.of(credential, config.referencedCredentialKeys());
        CredentialResolver credentials = listener -> listener.onResponse(snapshot);
        OAuth2TokenCache.TokenSource source = (values, allowlist, listener) -> tokenClient
            .requestToken(config, values, allowlist, listener);
        String tenantId = connector.getTenantId();
        // The configuration as it is written: one cache entry per configuration, whichever connectors hold it.
        String configKey = Strings.toString(MediaTypeRegistry.JSON, config);
        return new AuthorizationProvider() {
            @Override
            public void authorizationValue(ActionListener<TokenHandle> listener) {
                cache.get(tenantId, configKey, credentials, source, listener);
            }

            @Override
            public boolean invalidate(TokenHandle handle) {
                return cache.invalidate(handle);
            }
        };
    }
}
