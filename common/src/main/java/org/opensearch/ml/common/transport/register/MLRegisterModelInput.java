/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;


/**
 * ML input data: algirithm name, parameters and input data set.
 */
@Data
public class MLRegisterModelInput implements ToXContentObject, Writeable {

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String NAME_FIELD = "name";
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String VERSION_FIELD = "version";
    public static final String URL_FIELD = "url";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_CONFIG_FIELD = "model_config";
    public static final String DEPLOY_MODEL_FIELD = "deploy_model";
    public static final String MODEL_NODE_IDS_FIELD = "model_node_ids";

    private FunctionName functionName;
    private String modelName;
    private String modelGroupId;
    private String version;
    private String description;
    private String url;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;

    private boolean deployModel;
    private String[] modelNodeIds;

    @Builder(toBuilder = true)
    public MLRegisterModelInput(FunctionName functionName,
                                String modelName,
                                String modelGroupId,
                                String version,
                                String description,
                                String url,
                                MLModelFormat modelFormat,
                                MLModelConfig modelConfig,
                                boolean deployModel,
                                String[] modelNodeIds) {
        if (functionName == null) {
            this.functionName = FunctionName.TEXT_EMBEDDING;
        } else {
            this.functionName = functionName;
        }
        if (modelName == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (version == null && modelGroupId == null) {
            throw new IllegalArgumentException("model version is null");
        }
        if (modelFormat == null) {
            throw new IllegalArgumentException("model format is null");
        }
        if (url != null && modelConfig == null) {
            throw new IllegalArgumentException("model config is null");
        }
        this.modelName = modelName;
        this.modelGroupId = modelGroupId;
        this.version = version;
        this.description = description;
        this.url = url;
        this.modelFormat = modelFormat;
        this.modelConfig = modelConfig;
        this.deployModel = deployModel;
        this.modelNodeIds = modelNodeIds;
    }


    public MLRegisterModelInput(StreamInput in) throws IOException {
        this.functionName = in.readEnum(FunctionName.class);
        this.modelName = in.readString();
        this.modelGroupId = in.readOptionalString();
        this.version = in.readOptionalString();
        this.description = in.readOptionalString();
        this.url = in.readOptionalString();
        if (in.readBoolean()) {
            this.modelFormat = in.readEnum(MLModelFormat.class);
        }
        if (in.readBoolean()) {
            this.modelConfig = new TextEmbeddingModelConfig(in);
        }
        this.deployModel = in.readBoolean();
        this.modelNodeIds = in.readOptionalStringArray();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(functionName);
        out.writeString(modelName);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
        out.writeOptionalString(url);
        if (modelFormat != null) {
            out.writeBoolean(true);
            out.writeEnum(modelFormat);
        } else {
            out.writeBoolean(false);
        }
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeBoolean(deployModel);
        out.writeOptionalStringArray(modelNodeIds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FUNCTION_NAME_FIELD, functionName);
        builder.field(NAME_FIELD, modelName);
        builder.field(VERSION_FIELD, version);
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (modelFormat != null) {
            builder.field(MODEL_FORMAT_FIELD, modelFormat);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        builder.field(DEPLOY_MODEL_FIELD, deployModel);
        if (modelNodeIds != null) {
            builder.field(MODEL_NODE_IDS_FIELD, modelNodeIds);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelInput parse(XContentParser parser, String modelName, String version, boolean deployModel) throws IOException {
        FunctionName functionName = null;
        String url = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        List<String> modelNodeIds = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case FUNCTION_NAME_FIELD:
                    functionName = FunctionName.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                case MODEL_NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelNodeIds.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        String modelGroupId = null;//TODO: add model group id
        return new MLRegisterModelInput(functionName, modelName, modelGroupId, version, description, url, modelFormat, modelConfig, deployModel, modelNodeIds.toArray(new String[0]));
    }

    public static MLRegisterModelInput parse(XContentParser parser, boolean deployModel) throws IOException {
        FunctionName functionName = null;
        String name = null;
        String modelGroupId = null;
        String version = null;
        String url = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        List<String> modelNodeIds = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case FUNCTION_NAME_FIELD:
                    functionName = FunctionName.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case NAME_FIELD:
                    name = parser.text();
                    break;
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                case MODEL_NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelNodeIds.add(parser.text());
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelInput(functionName, name, modelGroupId, version, description, url, modelFormat, modelConfig, deployModel, modelNodeIds.toArray(new String[0]));
    }
}
