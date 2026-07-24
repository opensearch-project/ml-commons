/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorProtocols.GOOGLE_CLOUD;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

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

    public static final String AUTHORIZATION_HEADER = "Authorization";

    // Runtime-only bearer token set by the executor before each request. Excluded from
    // equals/hashCode and serialization: it is transient per-request state, not connector config.
    @EqualsAndHashCode.Exclude
    private transient String authorizationValue;

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
        String provisionedBy
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
            provisionedBy
        );
        validate();
    }

    public GoogleCloudConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        validate();
    }

    public GoogleCloudConnector(StreamInput input) throws IOException {
        super(input);
        validate();
    }

    public GoogleCloudConnector(String protocol, StreamInput input) throws IOException {
        super(protocol, input);
        // Validation skipped for StreamInput deserialization (mirrors AwsConnector).
    }

    private boolean isAdcMode() {
        return parameters != null && AUTH_MODE_ADC.equalsIgnoreCase(parameters.get(AUTH_MODE_FIELD));
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

    public String getScopes() {
        if (parameters == null || parameters.get(SCOPES_FIELD) == null) {
            return DEFAULT_SCOPE;
        }
        return parameters.get(SCOPES_FIELD);
    }

    /**
     * Set the bearer token the executor minted for the current request. Applied
     * programmatically (not via user ${parameters.*} substitution) so the framework's
     * blocked-dynamic-header guard on Authorization is not bypassed.
     */
    public void setAuthorizationValue(String authorizationValue) {
        this.authorizationValue = authorizationValue;
    }

    @Override
    public Map<String, String> getHeadersWithRuntimeParameters(Map<String, String> runtimeParameters) {
        Map<String, String> headers = super.getHeadersWithRuntimeParameters(runtimeParameters);
        if (authorizationValue == null) {
            return headers;
        }
        Map<String, String> withAuth = headers == null ? new HashMap<>() : new HashMap<>(headers);
        withAuth.put(AUTHORIZATION_HEADER, authorizationValue);
        return withAuth;
    }
}
