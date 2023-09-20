/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import lombok.Data;
import lombok.Builder;
import lombok.Getter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.Connector.createConnector;

@Data
public class MLUpdateModelInput implements ToXContentObject, Writeable {
    
    public static final String MODEL_ID_FIELD = "model_id"; // mandatory
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String MODEL_NAME_FIELD = "name"; // optional
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String MODEL_CONFIG_FIELD = "model_config"; // optional
    public static final String CONNECTOR_FIELD = "connector"; // optional Access control? Re-use?
    public static final String CONNECTOR_ID_FIELD = "connector_id"; // optional Access control? Case-switch?

    @Getter
    private String modelId;
    private String description;
    private String name;
    private String modelGroupId;
    private MLModelConfig modelConfig;
    private Connector connector;
    private String connectorId;

    @Builder(toBuilder = true)
    public MLUpdateModelInput(String modelId, String description, String name, String modelGroupId, MLModelConfig modelConfig, Connector connector, String connectorId) {
        this.modelId = modelId;
        this.description = description;
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.modelConfig = modelConfig;
        this.connector = connector;
        this.connectorId = connectorId;
    }

    public MLUpdateModelInput(StreamInput in) throws IOException {
        this.modelId = in.readString();
        this.description = in.readOptionalString();
        this.name = in.readOptionalString();
        this.modelGroupId = in.readOptionalString();
        if (in.readBoolean()) {
            modelConfig = new TextEmbeddingModelConfig(in);
        }
        if (in.readBoolean()) {
            connector = Connector.fromStream(in);
        }
        this.connectorId = in.readOptionalString();
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (name != null) {
            builder.field(MODEL_NAME_FIELD, name);
        }
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        if (connector != null) {
            builder.field(CONNECTOR_FIELD, connector);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeOptionalString(description);
        out.writeOptionalString(name);
        out.writeOptionalString(modelGroupId);
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (connector != null) {
            out.writeBoolean(true);
            connector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
    }

    public static MLUpdateModelInput parse(XContentParser parser, String modelId) throws IOException {
        MLUpdateModelInput input = new MLUpdateModelInput(modelId, null, null, null, null, null, null);
        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MODEL_ID_FIELD:
                    input.setModelId(parser.text());
                    break;
                case DESCRIPTION_FIELD:
                    input.setDescription(parser.text());
                    break;
                case MODEL_NAME_FIELD:
                    input.setName(parser.text());
                    break;
                case MODEL_GROUP_ID_FIELD:
                    input.setModelGroupId(parser.text());
                    break;
                case MODEL_CONFIG_FIELD:
                    input.setModelConfig(TextEmbeddingModelConfig.parse(parser));
                    break;
                case CONNECTOR_FIELD:
                    input.setConnector(createConnector(parser));
                    break;
                case CONNECTOR_ID_FIELD:
                    input.setConnectorId(parser.text());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return input;
    }
}