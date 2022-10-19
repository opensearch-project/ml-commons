/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
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
public class MLUploadInput implements ToXContentObject, Writeable {

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String URL_FIELD = "url";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_CONFIG_FIELD = "model_config";
    public static final String LOAD_MODEL_FIELD = "load_model";
    public static final String MODEL_NODE_IDS_FIELD = "model_node_ids";

    private FunctionName functionName;
    private String modelName;
    private String version;
    private String url;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;

    private boolean loadModel;
    private String[] modelNodeIds;

    @Builder(toBuilder = true)
    public MLUploadInput(FunctionName functionName, String modelName, String version, String url, MLModelFormat modelFormat, MLModelConfig modelConfig, boolean loadModel, String[] modelNodeIds) {
        if (functionName == null) {
            this.functionName = FunctionName.TEXT_EMBEDDING;
        }
        if (modelName == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (version == null) {
            throw new IllegalArgumentException("model version is null");
        }
        this.modelName = modelName;
        this.version = version;
        this.url = url;
        this.modelFormat = modelFormat;
        this.modelConfig = modelConfig;
        this.loadModel = loadModel;
        this.modelNodeIds = modelNodeIds;
    }


    public MLUploadInput(StreamInput in) throws IOException {
        this.functionName = in.readEnum(FunctionName.class);
        this.modelName = in.readString();
        this.version = in.readString();
        this.url = in.readOptionalString();
        if (in.readBoolean()) {
            this.modelFormat = in.readEnum(MLModelFormat.class);
        }
        if (in.readBoolean()) {
            this.modelConfig = new TextEmbeddingModelConfig(in);
        }
        this.loadModel = in.readBoolean();
        this.modelNodeIds = in.readOptionalStringArray();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(functionName);
        out.writeString(modelName);
        out.writeString(version);
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
        out.writeBoolean(loadModel);
        out.writeOptionalStringArray(modelNodeIds);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FUNCTION_NAME_FIELD, functionName);
        builder.field(NAME_FIELD, modelName);
        builder.field(VERSION_FIELD, version);
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (modelFormat != null) {
            builder.field(MODEL_FORMAT_FIELD, modelFormat);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        builder.field(LOAD_MODEL_FIELD, loadModel);
        if (modelNodeIds != null) {
            builder.field(MODEL_NODE_IDS_FIELD, modelNodeIds);
        }
        builder.endObject();
        return builder;
    }

    public static MLUploadInput parse(XContentParser parser, String modelName, String version, boolean loadModel) throws IOException {
        FunctionName functionName = null;
        String url = null;
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
        return new MLUploadInput(functionName, modelName, version, url, modelFormat, modelConfig, loadModel, modelNodeIds.toArray(new String[0]));
    }

    public static MLUploadInput parse(XContentParser parser, boolean loadModel) throws IOException {
        FunctionName functionName = null;
        String name = null;
        String version = null;
        String url = null;
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
                case VERSION_FIELD:
                    version = parser.text();
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
        return new MLUploadInput(functionName, name, version, url, modelFormat, modelConfig, loadModel, modelNodeIds.toArray(new String[0]));
    }
}
