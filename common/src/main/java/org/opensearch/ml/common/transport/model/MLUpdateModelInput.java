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
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;

import java.io.IOException;
import java.time.Instant;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUpdateModelInput implements ToXContentObject, Writeable {
    
    public static final String MODEL_ID_FIELD = "model_id"; // passively set when passing url to rest API
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String MODEL_VERSION_FIELD = "model_version"; // passively set when register model to a new model group
    public static final String MODEL_NAME_FIELD = "name"; // optional
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String IS_ENABLED_FIELD = "is_enabled"; // optional
    public static final String MODEL_RATE_LIMITER_CONFIG_FIELD = "model_rate_limiter_config"; // optional
    public static final String MODEL_CONFIG_FIELD = "model_config"; // optional
    public static final String CONNECTOR_FIELD = "connector"; // optional
    public static final String CONNECTOR_ID_FIELD = "connector_id"; // optional
    // The field CONNECTOR_UPDATE_CONTENT_FIELD need to be declared because the update of Connector class relies on the MLCreateConnectorInput class
    public static final String CONNECTOR_UPDATE_CONTENT_FIELD = "connector_update_content";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time"; // passively set when sending update request

    @Getter
    private String modelId;
    private String description;
    private String version;
    private String name;
    private String modelGroupId;
    private Boolean isEnabled;
    private MLRateLimiter modelRateLimiterConfig;
    private MLModelConfig modelConfig;
    private Connector connector;
    private String connectorId;
    private MLCreateConnectorInput connectorUpdateContent;
    private Instant lastUpdateTime;

    @Builder(toBuilder = true)
    public MLUpdateModelInput(String modelId, String description, String version, String name, String modelGroupId,
                              Boolean isEnabled, MLRateLimiter modelRateLimiterConfig, MLModelConfig modelConfig,
                              Connector connector, String connectorId, MLCreateConnectorInput connectorUpdateContent, Instant lastUpdateTime) {
        this.modelId = modelId;
        this.description = description;
        this.version = version;
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.isEnabled = isEnabled;
        this.modelRateLimiterConfig = modelRateLimiterConfig;
        this.modelConfig = modelConfig;
        this.connector = connector;
        this.connectorId = connectorId;
        this.connectorUpdateContent = connectorUpdateContent;
        this.lastUpdateTime = lastUpdateTime;
    }

    public MLUpdateModelInput(StreamInput in) throws IOException {
        modelId = in.readString();
        description = in.readOptionalString();
        version = in.readOptionalString();
        name = in.readOptionalString();
        modelGroupId = in.readOptionalString();
        isEnabled = in.readOptionalBoolean();
        if (in.readBoolean()) {
            modelRateLimiterConfig = new MLRateLimiter(in);
        }
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
        lastUpdateTime = in.readOptionalInstant();
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
        if (isEnabled != null) {
            builder.field(IS_ENABLED_FIELD, isEnabled);
        }
        if (modelRateLimiterConfig != null) {
            builder.field(MODEL_RATE_LIMITER_CONFIG_FIELD, modelRateLimiterConfig);
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
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
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
        out.writeOptionalBoolean(isEnabled);
        if (modelRateLimiterConfig != null) {
            out.writeBoolean(true);
            modelRateLimiterConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
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
        out.writeOptionalInstant(lastUpdateTime);
    }

    public static MLUpdateModelInput parse(XContentParser parser) throws IOException {
        String modelId = null;
        String description = null;
        String version = null;
        String name = null;
        String modelGroupId = null;
        Boolean isEnabled = null;
        MLRateLimiter modelRateLimiterConfig = null;
        MLModelConfig modelConfig = null;
        Connector connector = null;
        String connectorId = null;
        MLCreateConnectorInput connectorUpdateContent = null;
        Instant lastUpdateTime = null;

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
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case MODEL_RATE_LIMITER_CONFIG_FIELD:
                    modelRateLimiterConfig = MLRateLimiter.parse(parser);
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
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        // Model ID can only be set through RestRequest. Model version can only be set automatically.
        return new MLUpdateModelInput(modelId, description, version, name, modelGroupId, isEnabled, modelRateLimiterConfig, modelConfig, connector, connectorId, connectorUpdateContent, lastUpdateTime);
    }
}