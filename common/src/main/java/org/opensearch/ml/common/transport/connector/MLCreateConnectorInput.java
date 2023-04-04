/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.ConnectorAPIs;
import org.opensearch.ml.common.connector.ConnectorParams;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

public class MLCreateConnectorInput implements ToXContentObject, Writeable {

    public static final String CONNECTOR_PARAMETERS_FIELD = "Parameters";
    public static final String CONNECTOR_APIS_FIELD = "APIs";

    private ConnectorParams connectorParams;
    private ConnectorAPIs connectorAPIs;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(ConnectorParams connectorParams,
                                  ConnectorAPIs connectorAPIs) {
        this.connectorParams = connectorParams;
        this.connectorAPIs = connectorAPIs;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        ConnectorParams connectorParams = null;
        ConnectorAPIs connectorAPIs = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_PARAMETERS_FIELD:
                    connectorParams = ConnectorParams.parse(parser);
                    break;
                case CONNECTOR_APIS_FIELD:
                    connectorAPIs = ConnectorAPIs.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLCreateConnectorInput(connectorParams, connectorAPIs);
    }


    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (connectorParams != null) {
            builder.field(CONNECTOR_PARAMETERS_FIELD, connectorParams);
        }
        if (connectorAPIs != null) {
            builder.field(CONNECTOR_APIS_FIELD, connectorAPIs);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        if (connectorParams != null) {
            output.writeBoolean(true);
            connectorParams.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
        if (connectorAPIs != null) {
            output.writeBoolean(true);
            connectorAPIs.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
    }

    public MLCreateConnectorInput(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            this.connectorParams = new ConnectorParams(input);
        }
        if (input.readBoolean()) {
            this.connectorAPIs = new ConnectorAPIs(input);
        }
    }
}