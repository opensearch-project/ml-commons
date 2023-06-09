/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.connector;

import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.XContentParser;

import java.io.IOException;
import java.util.Optional;

import static org.opensearch.ml.common.connector.ConnectorNames.AWS_V1;

@Log4j2
@NoArgsConstructor
@org.opensearch.ml.common.annotation.Connector(AWS_V1)
public class AwsConnector extends HttpConnector {

    public static final String ACCESS_KEY_FIELD = "access_key";
    public static final String SECRET_KEY_FIELD = "secret_key";
    public static final String SERVICE_NAME_FIELD = "service_name";
    public static final String REGION_FIELD = "region";

    public AwsConnector(String name, XContentParser parser) throws IOException {
        super(name, parser);
        validate();
    }

    public AwsConnector(StreamInput input) throws IOException {
        super(input);
        validate();
    }

    private void validate() {
        headers.remove("Content-Type");
        if (credential == null || !credential.containsKey(ACCESS_KEY_FIELD) || !credential.containsKey(SECRET_KEY_FIELD)) {
            throw new IllegalArgumentException("Missing credential");
        }
    }

    @Override
    public Connector clone() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
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
