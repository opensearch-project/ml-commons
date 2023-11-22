/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
public class MLUpdateModelInput implements ToXContentObject, Writeable {

    public static final String MODEL_ID_FIELD = "model_id"; // mandatory
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String MODEL_VERSION_FIELD = "model_version"; // optional
    public static final String MODEL_NAME_FIELD = "name"; // optional
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String MODEL_CONFIG_FIELD = "model_config"; // optional
    public static final String CONNECTOR_ID_FIELD = "connector_id"; // optional

    @Getter
    private String modelId;
    private String description;
    private String version;
    private String name;
    private String modelGroupId;
    private MLModelConfig modelConfig;
    private String connectorId;

    @Builder(toBuilder = true)
    public MLUpdateModelInput(
        String modelId,
        String description,
        String version,
        String name,
        String modelGroupId,
        MLModelConfig modelConfig,
        String connectorId
    ) {
        this.modelId = modelId;
        this.description = description;
        this.version = version;
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.modelConfig = modelConfig;
        this.connectorId = connectorId;
    }

    public MLUpdateModelInput(StreamInput in) throws IOException {
        this.modelId = in.readString();
        this.description = in.readOptionalString();
        this.version = in.readOptionalString();
        this.name = in.readOptionalString();
        this.modelGroupId = in.readOptionalString();
        if (in.readBoolean()) {
            modelConfig = new TextEmbeddingModelConfig(in);
        }
        this.connectorId = in.readOptionalString();
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
        out.writeOptionalString(version);
        out.writeOptionalString(name);
        out.writeOptionalString(modelGroupId);
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
    }

    public static MLUpdateModelInput parse(XContentParser parser) throws IOException {
        String modelId = null;
        String description = null;
        String version = null;
        String name = null;
        String modelGroupId = null;
        MLModelConfig modelConfig = null;
        String connectorId = null;

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
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        // Model ID can only be set through RestRequest. Model version can only be set automatically.
        return new MLUpdateModelInput(modelId, description, version, name, modelGroupId, modelConfig, connectorId);
    }
}
