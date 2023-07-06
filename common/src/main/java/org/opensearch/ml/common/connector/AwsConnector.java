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

import static org.opensearch.ml.common.connector.ConnectorProtocols.AWS_SIGV4;

@Log4j2
@NoArgsConstructor
@org.opensearch.ml.common.annotation.Connector(AWS_SIGV4)
public class AwsConnector extends HttpConnector {

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
    }

    @Override
    public Connector cloneConnector() {
        try (BytesStreamOutput bytesStreamOutput = new BytesStreamOutput()){
            this.writeTo(bytesStreamOutput);
            StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
            return new AwsConnector(streamInput);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
