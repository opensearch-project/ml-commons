/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
@EqualsAndHashCode
@org.opensearch.ml.common.annotation.Connector(AWS_SIGV4)
public class AwsConnector extends HttpConnector {

    @Builder(builderMethodName = "awsConnectorBuilder")
    public AwsConnector(
        String name,
        String description,
        String version,
        String protocol,
        Map<String, String> parameters,
        Map<String, String> credential,
        List<ConnectorAction> actions,
        List<String> backendRoles,
        AccessMode accessMode,
        User owner
    ) {
        super(name, description, version, protocol, parameters, credential, actions, backendRoles, accessMode, owner);
        validate();
    }

    public AwsConnector(String protocol, XContentParser parser) throws IOException {
        super(protocol, parser);
        validate();
    }

    public AwsConnector(StreamInput input) throws IOException {
        super(input);
        validate();
    }

    private void validate() {
        if (credential == null || !credential.containsKey(ACCESS_KEY_FIELD) || !credential.containsKey(SECRET_KEY_FIELD)) {
            throw new IllegalArgumentException("Missing credential");
        }
        if ((credential == null || !credential.containsKey(SERVICE_NAME_FIELD))
            && (parameters == null || !parameters.containsKey(SERVICE_NAME_FIELD))) {
            throw new IllegalArgumentException("Missing service name");
        }
        if ((credential == null || !credential.containsKey(REGION_FIELD))
            && (parameters == null || !parameters.containsKey(REGION_FIELD))) {
            throw new IllegalArgumentException("Missing region");
        }
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()) {
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new AwsConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getAccessKey() {
        return decryptedCredential.get(ACCESS_KEY_FIELD);
    }

    public String getSecretKey() {
        return decryptedCredential.get(SECRET_KEY_FIELD);
    }

    public String getSessionToken() {
        return decryptedCredential.get(SESSION_TOKEN_FIELD);
    }

    public String getServiceName() {
        if (parameters == null) {
            return decryptedCredential.get(SERVICE_NAME_FIELD);
        }
        return Optional.ofNullable(parameters.get(SERVICE_NAME_FIELD)).orElse(decryptedCredential.get(SERVICE_NAME_FIELD));
    }

    public String getRegion() {
        if (parameters == null) {
            return decryptedCredential.get(REGION_FIELD);
        }
        return Optional.ofNullable(parameters.get(REGION_FIELD)).orElse(decryptedCredential.get(REGION_FIELD));
    }
}
