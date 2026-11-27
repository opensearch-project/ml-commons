/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS;

import java.util.List;
import java.util.Set;

/**
 * The extension grants (RFC 6749 section 4.5) a connector {@code auth_config} may request a token with, the value of
 * {@code plugins.ml_commons.connector.oauth2_extension_grants}. The two grants the plugin implements itself, client
 * credentials and JWT bearer, are admitted without an entry. Compared as written: grant URIs are case-sensitive.
 */
public final class ExtensionGrantAllowlist {

    public static final String NOT_ADMITTED_MESSAGE = "auth_config.grant_type is an extension grant that "
        + ML_COMMONS_CONNECTOR_OAUTH2_EXTENSION_GRANTS.getKey()
        + " does not admit";

    private final Set<String> grants;

    /** @param grants the setting's value, whose validator has already checked every entry */
    public ExtensionGrantAllowlist(List<String> grants) {
        this.grants = Set.copyOf(grants);
    }

    /** Whether a token may be requested with {@code grantType}. */
    public boolean admits(String grantType) {
        return OAuth2AuthConfig.GRANT_TYPE_CLIENT_CREDENTIALS.equals(grantType)
            || OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER.equals(grantType)
            || grants.contains(grantType);
    }
}
