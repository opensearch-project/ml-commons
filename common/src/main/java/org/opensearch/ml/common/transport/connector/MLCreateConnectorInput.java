/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.template.ConnectorTemplate;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLCreateConnectorInput implements ToXContentObject, Writeable {
    public static final String CONNECTOR_META_DATA_FIELD = "metadata";
    public static final String CONNECTOR_PARAMETERS_FIELD = "parameters";
    public static final String CONNECTOR_CREDENTIAL_FIELD = "credential";
    public static final String CONNECTOR_TEMPLATE_FIELD = "template";

    private Map<String, String> metadata;
    private Map<String, String> parameters;
    private Map<String, String> credential;
    private ConnectorTemplate connectorTemplate;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(Map<String, String> metadata,
                                  Map<String, String> parameters,
                                  Map<String, String> credential,
                                  ConnectorTemplate connectorTemplate) {
        this.metadata = metadata;
        this.parameters = parameters;
        this.credential = credential;
        this.connectorTemplate = connectorTemplate;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> credential = new HashMap<>();
        ConnectorTemplate connectorTemplate = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case CONNECTOR_META_DATA_FIELD:
                    metadata = parser.mapStrings();
                    break;
                case CONNECTOR_PARAMETERS_FIELD:
                    parameters = parser.mapStrings();
                    break;
                case CONNECTOR_CREDENTIAL_FIELD:
                    credential = parser.mapStrings();
                    break;
                case CONNECTOR_TEMPLATE_FIELD:
                    connectorTemplate = connectorTemplate.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLCreateConnectorInput(metadata, parameters, credential, connectorTemplate);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        if (metadata != null) {
            builder.field(CONNECTOR_META_DATA_FIELD, metadata);
        }
        if (parameters != null) {
            builder.field(CONNECTOR_PARAMETERS_FIELD, parameters);
        }
        if (credential != null) {
            builder.field(CONNECTOR_CREDENTIAL_FIELD, credential);
        }
        if (connectorTemplate != null) {
            builder.field(CONNECTOR_TEMPLATE_FIELD, connectorTemplate);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput output) throws IOException {
        if (metadata != null) {
            output.writeBoolean(true);
            output.writeMap(metadata, StreamOutput::writeString, StreamOutput::writeString);
        }
        else {
            output.writeBoolean(false);
        }
        if (parameters != null) {
            output.writeBoolean(true);
            output.writeMap(parameters, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        if (credential != null) {
            output.writeBoolean(true);
            output.writeMap(credential, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            output.writeBoolean(false);
        }
        if (connectorTemplate != null) {
            output.writeBoolean(true);
            connectorTemplate.writeTo(output);
        } else {
            output.writeBoolean(false);
        }
    }

    public MLCreateConnectorInput(StreamInput input) throws IOException {
        if (input.readBoolean()) {
            metadata = input.readMap(s -> s.readString(), s-> s.readString());
        }
        if (input.readBoolean()) {
            parameters = input.readMap(s -> s.readString(), s -> s.readString());
        }
        if (input.readBoolean()) {
            credential = input.readMap(s -> s.readString(), s-> s.readString());
        }
        if (input.readBoolean()) {
            this.connectorTemplate = new ConnectorTemplate(input);
        }
    }
}
