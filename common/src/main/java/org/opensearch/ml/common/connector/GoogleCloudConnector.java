/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorProtocols.GOOGLE_CLOUD;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.connector.auth.ConnectorAuthType;
import org.opensearch.ml.common.connector.auth.CredentialReference;
import org.opensearch.ml.common.connector.auth.JwtSpec;
import org.opensearch.ml.common.connector.auth.OAuth2AuthConfig;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * A Vertex AI connector. Its identity is either an explicit {@code auth_config}, judged and stored as on an http
 * connector, or the legacy form: a service account whose {@code private_key}, {@code client_email} and optional
 * {@code token_uri} the credential holds, or application default credentials ({@code auth_mode: adc}). A legacy
 * connector stores no {@code auth_config}; the configuration its executor uses is derived from the stored fields
 * ({@link #getEffectiveAuthConfig()}).
 */
@Log4j2
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@org.opensearch.ml.common.annotation.Connector(GOOGLE_CLOUD)
public class GoogleCloudConnector extends HttpConnector {

    public static final String PRIVATE_KEY_FIELD = "private_key";
    public static final String CLIENT_EMAIL_FIELD = "client_email";
    public static final String TOKEN_URI_FIELD = "token_uri";
    public static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    public static final String AUTH_MODE_FIELD = "auth_mode";
    public static final String AUTH_MODE_ADC = "adc";
    public static final String SCOPES_FIELD = "scopes";
    public static final String DEFAULT_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    /** The claim a service account assertion carries its scopes in, as the Google auth library sends it. */
    public static final String SCOPE_CLAIM = "scope";
    /** The lifetime the Google auth library gives a service account assertion. */
    public static final int SERVICE_ACCOUNT_ASSERTION_LIFETIME_SECONDS = 3600;

    // Vertex batch_predict URLs embed an API version segment (/v1/ or /v1beta<N>/) used to derive the
    // status/cancel URL. Checked on create/update (see validateConnectorURL) so a malformed endpoint fails
    // before a job is submitted rather than after, which would orphan a Vertex billing job. Mirrors the runtime
    // check in ConnectorUtils; duplicated here because common cannot depend on ml-algorithms.
    private static final Pattern VERTEX_API_VERSION_PATTERN = Pattern.compile("/v1(beta\\d+)?/");

    @Builder(builderMethodName = "googleCloudConnectorBuilder")
    public GoogleCloudConnector(
        String name,
        String description,
        String version,
        String protocol,
        Map<String, String> parameters,
        Map<String, String> credential,
        List<ConnectorAction> actions,
        List<String> backendRoles,
        AccessMode accessMode,
        User owner,
        ConnectorClientConfig connectorClientConfig,
        String tenantId,
        String provisionedBy,
        OAuth2AuthConfig authConfig
    ) {
        super(
            name,
            description,
            version,
            protocol,
            parameters,
            credential,
            actions,
            backendRoles,
            accessMode,
            owner,
            connectorClientConfig,
            tenantId,
            provisionedBy,
            authConfig
        );
        validateLegacyIdentity();
    }

    public GoogleCloudConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        validateLegacyIdentity();
    }

    public GoogleCloudConnector(StreamInput input) throws IOException {
        super(input);
        validateLegacyIdentity();
    }

    public GoogleCloudConnector(String protocol, StreamInput input) throws IOException {
        super(protocol, input);
        // Validation is intentionally skipped on this wire-deserialization path (mirrors AwsConnector):
        // a connector arriving over the transport layer was already validated at creation time via the
        // builder / XContent constructors, so re-validating a decrypted-then-reserialized copy is
        // unnecessary and could reject an otherwise-valid connector. The validating StreamInput
        // constructor above is used for the create/clone paths; this protocol-prefixed one is the
        // node-to-node deserialization path (see Connector.createConnector / fromStream dispatch).
    }

    private boolean isAdcMode() {
        return parameters != null && AUTH_MODE_ADC.equalsIgnoreCase(parameters.get(AUTH_MODE_FIELD));
    }

    /** The legacy identity is required only of a connector without an explicit {@code auth_config}. */
    private void validateLegacyIdentity() {
        if (getAuthConfig() == null) {
            validate();
        }
    }

    private void validate() {
        if (isAdcMode()) {
            // ADC mode: no service-account credentials expected. Reject a mix.
            if (credential != null && (credential.containsKey(PRIVATE_KEY_FIELD) || credential.containsKey(CLIENT_EMAIL_FIELD))) {
                throw new IllegalArgumentException("auth_mode=adc must not include service-account credentials (private_key/client_email)");
            }
            return;
        }
        // Service-account key mode.
        if (credential == null || !credential.containsKey(PRIVATE_KEY_FIELD) || !credential.containsKey(CLIENT_EMAIL_FIELD)) {
            throw new IllegalArgumentException("Missing credential");
        }
    }

    /**
     * The explicit {@code auth_config} when there is one, even {@code none}; otherwise the configuration the legacy
     * identity stands for, built from the stored fields on every call and holding references only: {@code google_adc}
     * for {@code auth_mode: adc}, else the JWT bearer grant with the service account as issuer, its key, the token URI
     * the credential holds (the default when it holds none), the scopes as the single {@code scope} claim and the
     * assertion lifetime of the Google auth library. The library addresses the assertion to the standard Google token
     * endpoint whatever token server URI it is configured with, so a credential that holds its own {@code token_uri}
     * gets that endpoint as the audience; without one, the signer uses the effective token URL, which is that endpoint.
     */
    @Override
    public OAuth2AuthConfig getEffectiveAuthConfig() {
        OAuth2AuthConfig explicit = getAuthConfig();
        if (explicit != null) {
            return explicit;
        }
        if (isAdcMode()) {
            return OAuth2AuthConfig.builder().type(ConnectorAuthType.GOOGLE_ADC).build();
        }
        boolean ownTokenUri = hasCredentialKey(TOKEN_URI_FIELD);
        return OAuth2AuthConfig
            .builder()
            .type(ConnectorAuthType.OAUTH2)
            .tokenUrl(ownTokenUri ? reference(TOKEN_URI_FIELD) : DEFAULT_TOKEN_URI)
            .grantType(OAuth2AuthConfig.GRANT_TYPE_JWT_BEARER)
            .clientAuthMethod(OAuth2AuthConfig.CLIENT_AUTH_METHOD_NONE)
            .jwt(
                JwtSpec
                    .builder()
                    .issuer(reference(CLIENT_EMAIL_FIELD))
                    .audience(ownTokenUri ? DEFAULT_TOKEN_URI : null)
                    .privateKey(reference(PRIVATE_KEY_FIELD))
                    .signingAlg(JwtSpec.DEFAULT_SIGNING_ALG)
                    .lifetimeSeconds(SERVICE_ACCOUNT_ASSERTION_LIFETIME_SECONDS)
                    .additionalClaims(Map.of(SCOPE_CLAIM, getScopes()))
                    .build()
            )
            .build();
    }

    /** Whether the credential, stored or decrypted, holds {@code key}; the two hold the same keys. */
    private boolean hasCredentialKey(String key) {
        Map<String, String> keys = credential != null ? credential : decryptedCredential;
        return keys != null && keys.containsKey(key);
    }

    private static String reference(String key) {
        return CredentialReference.PREFIX + key + CredentialReference.SUFFIX;
    }

    /**
     * Adds the batch_predict version-segment check to the trusted-endpoint check. Deliberately not part of
     * {@link #validate()}: the constructors also run when a stored connector is read back from the index, and a
     * connector saved before this check existed must stay readable and deletable, and fixable through an update.
     * This method runs only where a connector is created or modified (connector create/update, inline connector
     * on model register/update). The URL is resolved against the connector parameters first, as the runtime
     * check does.
     */
    @Override
    public void validateConnectorURL(List<String> urlRegexes) {
        super.validateConnectorURL(urlRegexes);
        for (ConnectorAction action : actions) {
            if (action.getActionType() == ConnectorAction.ActionType.BATCH_PREDICT && action.getUrl() != null) {
                String url = Connector.substituteParameters(action.getUrl(), parameters);
                if (!VERTEX_API_VERSION_PATTERN.matcher(url).find()) {
                    throw new IllegalArgumentException(
                        "Vertex AI batch_predict endpoint must contain a '/v1/' or '/v1beta<N>/' path segment, got: " + url
                    );
                }
            }
        }
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new GoogleCloudConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean useAdc() {
        return isAdcMode();
    }

    public String getPrivateKey() {
        return decryptedCredential.get(PRIVATE_KEY_FIELD);
    }

    public String getClientEmail() {
        return decryptedCredential.get(CLIENT_EMAIL_FIELD);
    }

    public String getTokenUri() {
        String uri = decryptedCredential.get(TOKEN_URI_FIELD);
        return uri != null ? uri : DEFAULT_TOKEN_URI;
    }

    /** The scopes as stored, one string; several scopes are written space-separated, as the {@code scope} claim has them. */
    public String getScopes() {
        if (parameters == null || parameters.get(SCOPES_FIELD) == null) {
            return DEFAULT_SCOPE;
        }
        return parameters.get(SCOPES_FIELD);
    }
}
