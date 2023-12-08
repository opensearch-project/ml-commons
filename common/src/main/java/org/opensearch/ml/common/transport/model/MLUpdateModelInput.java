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
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUpdateModelInput implements ToXContentObject, Writeable {
    
    public static final String MODEL_ID_FIELD = "model_id"; // mandatory
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String MODEL_VERSION_FIELD = "model_version"; // optional
    public static final String MODEL_NAME_FIELD = "name"; // optional
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String MODEL_CONFIG_FIELD = "model_config"; // optional
    public static final String CONNECTOR_FIELD = "connector"; // optional
    public static final String CONNECTOR_ID_FIELD = "connector_id"; // optional
    public static final String CONNECTOR_UPDATE_CONTENT_FIELD = "connector_update_content"; // optional

    @Getter
    private String modelId;
    private String description;
    private String version;
    private String name;
    private String modelGroupId;
    private MLModelConfig modelConfig;
    private Connector connector;
    private String connectorId;
    private MLCreateConnectorInput connectorUpdateContent;

    @Builder(toBuilder = true)
    public MLUpdateModelInput(String modelId, String description, String version, String name, String modelGroupId,
                              MLModelConfig modelConfig,
                              Connector connector, String connectorId, MLCreateConnectorInput connectorUpdateContent) {
        this.modelId = modelId;
        this.description = description;
        this.version = version;
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.modelConfig = modelConfig;
        this.connector = connector;
        this.connectorId = connectorId;
        this.connectorUpdateContent = connectorUpdateContent;
    }

    public MLUpdateModelInput(StreamInput in) throws IOException {
        modelId = in.readString();
        description = in.readOptionalString();
        version = in.readOptionalString();
        name = in.readOptionalString();
        modelGroupId = in.readOptionalString();
        if (in.readBoolean()) {
            modelConfig = new TextEmbeddingModelConfig(in);
        }
        if (in.readBoolean()) {
            connector = Connector.fromStream(in);
        }
        connectorId = in.readOptionalString();
        if (in.readBoolean()) {
            connectorUpdateContent = new MLCreateConnectorInput(in);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_ID_FIELD, modelId);
        if (name != null) {
            builder.field(MODEL_NAME_FIELD, name);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (version != null) {
            builder.field(MODEL_VERSION_FIELD, version);
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
        if (connectorUpdateContent != null) {
            builder.field(CONNECTOR_UPDATE_CONTENT_FIELD, connectorUpdateContent);
        }
        builder.endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(modelId);
        out.writeOptionalString(description);
        out.writeOptionalString(version);
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
        if (connectorUpdateContent != null) {
            out.writeBoolean(true);
            connectorUpdateContent.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
    }

    public static MLUpdateModelInput parse(XContentParser parser) throws IOException {
        String modelId = null;
        String description = null;
        String version = null;
        String name = null;
        String modelGroupId = null;
        MLModelConfig modelConfig = null;
        Connector connector = null;
        String connectorId = null;
        MLCreateConnectorInput connectorUpdateContent = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MODEL_NAME_FIELD:
                    name = parser.text();
                    break;
                case MODEL_VERSION_FIELD:
                    version = parser.text();
                    break;
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                case CONNECTOR_FIELD:
                    connector = Connector.createConnector(parser);
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case CONNECTOR_UPDATE_CONTENT_FIELD:
                    connectorUpdateContent = MLCreateConnectorInput.parse(parser, true);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        // Model ID can only be set through RestRequest. Model version can only be set automatically.
        return new MLUpdateModelInput(modelId, description, version, name, modelGroupId, modelConfig, connector, connectorId, connectorUpdateContent);
    }
}