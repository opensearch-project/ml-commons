/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model.upload_chunk;

import lombok.Builder;
import lombok.Data;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.xcontent.ToXContentObject;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.FunctionName;

import java.io.IOException;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLUploadModelMetaInput implements ToXContentObject, Writeable{

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String MODEL_NAME_FIELD = "name"; //mandatory
    public static final String MODEL_VERSION_FIELD = "version"; //mandatory
    public static final String DESCRIPTION_FIELD = "description";
    public static final String MODEL_FORMAT_FIELD = "model_format"; //mandatory
    public static final String MODEL_STATE_FIELD = "model_state"; //mandatory
    public static final String MODEL_CONTENT_SIZE_IN_BYTES_FIELD = "model_content_size_in_bytes"; //mandatory
    public static final String MODEL_CONTENT_HASH_FIELD = "model_content_hash"; //mandatory
    public static final String MODEL_CONFIG_FIELD = "model_config"; //mandatory
    public static final String TOTAL_CHUNKS_FIELD = "total_chunks"; //mandatory

    private FunctionName functionName;
    private String name;
    private Integer version;
    private String description;

    private MLModelFormat modelFormat;

    private MLModelState modelState;

    private Long modelContentSizeInBytes;
    private String modelContentHash;
    private MLModelConfig modelConfig;
    private Integer totalChunks;

    @Builder(toBuilder = true)
    public MLUploadModelMetaInput(String name, FunctionName functionName, Integer version, String description, MLModelFormat modelFormat, MLModelState modelState, Long modelContentSizeInBytes, String modelContentHash, MLModelConfig modelConfig, Integer totalChunks) {
        this.name = name;
        this.functionName = functionName;
        this.version = version;
        this.description = description;
        this.modelFormat = modelFormat;
        this.modelState = modelState;
        this.modelContentSizeInBytes = modelContentSizeInBytes;
        this.modelContentHash = modelContentHash;
        this.modelConfig = modelConfig;
        this.totalChunks = totalChunks;
    }

    public MLUploadModelMetaInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.functionName = in.readEnum(FunctionName.class);
        this.version = in.readInt();
        this.totalChunks = in.readInt();
        this.description = in.readOptionalString();
        if (in.readBoolean()) {
            modelFormat = in.readEnum(MLModelFormat.class);
        }
        if (in.readBoolean()) {
            modelState = in.readEnum(MLModelState.class);
        }
        this.modelContentSizeInBytes = in.readLong();
        this.modelContentHash = in.readString();
        if (in.readBoolean()) {
            modelConfig = new TextEmbeddingModelConfig(in);
        }
        this.totalChunks = in.readInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeEnum(functionName);
        out.writeInt(version);
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
        out.writeLong(modelContentSizeInBytes);
        out.writeString(modelContentHash);
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
        if (name != null) {
            builder.field(MODEL_NAME_FIELD, name);
        }
        if (functionName != null) {
            builder.field(FUNCTION_NAME_FIELD, functionName);
        }
        if (version != null) {
            builder.field(MODEL_VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (modelFormat != null) {
            builder.field(MODEL_FORMAT_FIELD, modelFormat);
        }
        if (modelState != null) {
            builder.field(MODEL_STATE_FIELD, modelState);
        }
        if (modelContentSizeInBytes != null) {
            builder.field(MODEL_CONTENT_SIZE_IN_BYTES_FIELD, modelContentSizeInBytes);
        }
        if (modelContentHash != null) {
            builder.field(MODEL_CONTENT_HASH_FIELD, modelContentHash);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        if (totalChunks != null) {
            builder.field(TOTAL_CHUNKS_FIELD, totalChunks);
        }
        builder.endObject();
        return builder;
    }

    public static MLUploadModelMetaInput parse(XContentParser parser) throws IOException {
        String name = null;
        FunctionName functionName = null;
        Integer version = null;
        String description = null;;
        MLModelFormat modelFormat = null;
        MLModelState modelState = null;
        Long modelContentSizeInBytes = null;
        String modelContentHash = null;
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
                    version = parser.intValue(false);
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
                case MODEL_CONTENT_HASH_FIELD:
                    modelContentHash = parser.text();
                    break;
                case TOTAL_CHUNKS_FIELD:
                    totalChunks = parser.intValue(false);
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = TextEmbeddingModelConfig.parse(parser);
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLUploadModelMetaInput.builder()
                .name(name)
                .functionName(functionName)
                .version(version)
                .description(description)
                .modelFormat(modelFormat)
                .modelState(modelState)
                .modelContentSizeInBytes(modelContentSizeInBytes)
                .modelContentHash(modelContentHash)
                .modelConfig(modelConfig)
                .totalChunks(totalChunks)
                .build();
    }

}
