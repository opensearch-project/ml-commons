/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

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
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLRegisterModelMetaInput implements ToXContentObject, Writeable{

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String MODEL_NAME_FIELD = "name"; //mandatory
    public static final String MODEL_VERSION_FIELD = "version"; //mandatory
    public static final String DESCRIPTION_FIELD = "description";
    public static final String MODEL_FORMAT_FIELD = "model_format"; //mandatory
    public static final String MODEL_STATE_FIELD = "model_state";
    public static final String MODEL_CONTENT_SIZE_IN_BYTES_FIELD = "model_content_size_in_bytes";
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value"; //mandatory
    public static final String MODEL_CONFIG_FIELD = "model_config"; //mandatory
    public static final String TOTAL_CHUNKS_FIELD = "total_chunks"; //mandatory

    private FunctionName functionName;
    private String name;
    private String version;
    private String description;

    private MLModelFormat modelFormat;

    private MLModelState modelState;

    private Long modelContentSizeInBytes;
    private String modelContentHashValue;
    private MLModelConfig modelConfig;
    private Integer totalChunks;

    @Builder(toBuilder = true)
    public MLRegisterModelMetaInput(String name, FunctionName functionName, String version, String description, MLModelFormat modelFormat, MLModelState modelState, Long modelContentSizeInBytes, String modelContentHashValue, MLModelConfig modelConfig, Integer totalChunks) {
        if (name == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (functionName == null) {
            this.functionName = functionName.TEXT_EMBEDDING;
        } else {
            this.functionName = functionName;
        }
        if (version == null) {
            throw new IllegalArgumentException("model version is null");
        }
        if (modelFormat == null) {
            throw new IllegalArgumentException("model format is null");
        }
        if (modelContentHashValue == null) {
            throw new IllegalArgumentException("model content hash value is null");
        }
        if (modelConfig == null) {
            throw new IllegalArgumentException("model config is null");
        }
        if (totalChunks == null) {
            throw new IllegalArgumentException("total chunks field is null");
        }
        this.name = name;
        this.version = version;
        this.description = description;
        this.modelFormat = modelFormat;
        this.modelState = modelState;
        this.modelContentSizeInBytes = modelContentSizeInBytes;
        this.modelContentHashValue = modelContentHashValue;
        this.modelConfig = modelConfig;
        this.totalChunks = totalChunks;
    }

    public MLRegisterModelMetaInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.functionName = in.readEnum(FunctionName.class);
        this.version = in.readString();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            modelFormat = in.readEnum(MLModelFormat.class);
        }
        if (in.readBoolean()) {
            modelState = in.readEnum(MLModelState.class);
        }
        this.modelContentSizeInBytes = in.readOptionalLong();
        this.modelContentHashValue = in.readString();
        if (in.readBoolean()) {
            modelConfig = new TextEmbeddingModelConfig(in);
        }
        this.totalChunks = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeEnum(functionName);
        out.writeString(version);
        out.writeOptionalString(description);
        if (modelFormat != null) {
            out.writeBoolean(true);
            out.writeEnum(modelFormat);
        } else {
            out.writeBoolean(false);
        }
        if (modelState != null) {
            out.writeBoolean(true);
            out.writeEnum(modelState);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalLong(modelContentSizeInBytes);
        out.writeString(modelContentHashValue);
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeInt(totalChunks);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_NAME_FIELD, name);
        builder.field(FUNCTION_NAME_FIELD, functionName);
        builder.field(MODEL_VERSION_FIELD, version);
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        builder.field(MODEL_FORMAT_FIELD, modelFormat);
        if (modelState != null) {
            builder.field(MODEL_STATE_FIELD, modelState);
        }
        if (modelContentSizeInBytes != null) {
            builder.field(MODEL_CONTENT_SIZE_IN_BYTES_FIELD, modelContentSizeInBytes);
        }
        builder.field(MODEL_CONTENT_HASH_VALUE_FIELD, modelContentHashValue);
        builder.field(MODEL_CONFIG_FIELD, modelConfig);
        builder.field(TOTAL_CHUNKS_FIELD, totalChunks);
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelMetaInput parse(XContentParser parser) throws IOException {
        String name = null;
        FunctionName functionName = null;
        String version = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelState modelState = null;
        Long modelContentSizeInBytes = null;
        String modelContentHashValue = null;
        MLModelConfig modelConfig = null;
        Integer totalChunks = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case MODEL_NAME_FIELD:
                    name = parser.text();
                    break;
                case FUNCTION_NAME_FIELD:
                    functionName = FunctionName.from(parser.text());
                    break;
                case MODEL_VERSION_FIELD:
                    version = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text());
                    break;
                case MODEL_STATE_FIELD:
                    modelState = MLModelState.from(parser.text());
                    break;
                case MODEL_CONTENT_SIZE_IN_BYTES_FIELD:
                    modelContentSizeInBytes = parser.longValue();
                    break;
                case MODEL_CONTENT_HASH_VALUE_FIELD:
                    modelContentHashValue = parser.text();
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                case TOTAL_CHUNKS_FIELD:
                    totalChunks = parser.intValue(false);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelMetaInput(name, functionName, version, description, modelFormat, modelState, modelContentSizeInBytes, modelContentHashValue, modelConfig, totalChunks);
    }

}
