/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector.auth;

import static org.opensearch.ml.common.settings.MLCommonsSettings.ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX;

import java.util.List;
import java.util.regex.Pattern;

/** The trusted token endpoint patterns at one point in time. A token URL is trusted when its canonical form fully matches one. */
public final class TokenEndpointAllowlist {
    public static final String NOT_ALLOWED_MESSAGE = "auth_config.token_url does not match "
        + ML_COMMONS_TRUSTED_OAUTH2_TOKEN_ENDPOINTS_REGEX.getKey();

    private final List<Pattern> patterns;

    /** @param regexes the setting's value, whose validator has already checked every pattern */
    public TokenEndpointAllowlist(List<String> regexes) {
        this.patterns = regexes.stream().map(Pattern::compile).toList();
    }

    /** @param canonicalTokenUrl a URL in the form {@link OAuth2AuthConfig#canonicalTokenUrl} returns */
    public boolean matches(String canonicalTokenUrl) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(canonicalTokenUrl).matches());
    }
}
