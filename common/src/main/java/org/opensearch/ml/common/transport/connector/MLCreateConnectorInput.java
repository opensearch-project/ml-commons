/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.connector;

import com.google.gson.Gson;
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
    public static final String CONNECTOR_META_DATA_FIELD = "Metadata";
    public static final String CONNECTOR_PARAMETERS_FIELD = "Parameters";
    public static final String CONNECTOR_TEMPLATE_FIELD = "Template";

    private Map<String, String> metadata;
    private Map<String, String> parameters;
    private ConnectorTemplate connectorTemplate;

    @Builder(toBuilder = true)
    public MLCreateConnectorInput(Map<String, String> metadata,
                                  Map<String, String> parameters,
                                  ConnectorTemplate connectorTemplate) {
        this.metadata = metadata;
        this.parameters = parameters;
        this.connectorTemplate = connectorTemplate;
    }

    public static MLCreateConnectorInput parse(XContentParser parser) throws IOException {
        Map<String, String> metadata = new HashMap<>();
        Map<String, String> parameters = new HashMap<>();
        Gson gson = new Gson();
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
                case CONNECTOR_TEMPLATE_FIELD:
                    connectorTemplate = connectorTemplate.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }

        return new MLCreateConnectorInput(metadata, parameters, connectorTemplate);
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
            this.connectorTemplate = new ConnectorTemplate(input);
        }
    }
}
