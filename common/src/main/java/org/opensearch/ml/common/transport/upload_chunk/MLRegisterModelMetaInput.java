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
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.opensearch.common.xcontent.XContentParserUtils.ensureExpectedToken;

@Data
public class MLRegisterModelMetaInput implements ToXContentObject, Writeable{

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String MODEL_NAME_FIELD = "name"; //mandatory
    public static final String DESCRIPTION_FIELD = "description"; //optional

    public static final String VERSION_FIELD = "version";
    public static final String MODEL_FORMAT_FIELD = "model_format"; //mandatory
    public static final String MODEL_STATE_FIELD = "model_state";
    public static final String MODEL_CONTENT_SIZE_IN_BYTES_FIELD = "model_content_size_in_bytes";
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value"; //mandatory
    public static final String MODEL_CONFIG_FIELD = "model_config"; //mandatory
    public static final String TOTAL_CHUNKS_FIELD = "total_chunks"; //mandatory
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; //optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; //optional
    public static final String MODEL_ACCESS_MODE = "access_mode"; //optional
    public static final String ADD_ALL_BACKEND_ROLES = "add_all_backend_roles"; //optional

    private FunctionName functionName;
    private String name;

    private String modelGroupId;
    private String description;
    private String version;

    private MLModelFormat modelFormat;

    private MLModelState modelState;

    private Long modelContentSizeInBytes;
    private String modelContentHashValue;
    private MLModelConfig modelConfig;
    private Integer totalChunks;
    private List<String> backendRoles;
    private AccessMode modelAccessMode;
    private Boolean isAddAllBackendRoles;

    @Builder(toBuilder = true)
    public MLRegisterModelMetaInput(String name, FunctionName functionName, String modelGroupId, String version, String description, MLModelFormat modelFormat, MLModelState modelState, Long modelContentSizeInBytes, String modelContentHashValue, MLModelConfig modelConfig, Integer totalChunks, List<String> backendRoles,
                                    AccessMode modelAccessMode,
                                    Boolean isAddAllBackendRoles) {
        if (name == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (functionName == null) {
            this.functionName = functionName.TEXT_EMBEDDING;
        } else {
            this.functionName = functionName;
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
        this.modelGroupId = modelGroupId;
        this.version = version;
        this.description = description;
        this.modelFormat = modelFormat;
        this.modelState = modelState;
        this.modelContentSizeInBytes = modelContentSizeInBytes;
        this.modelContentHashValue = modelContentHashValue;
        this.modelConfig = modelConfig;
        this.totalChunks = totalChunks;
        this.backendRoles = backendRoles;
        this.modelAccessMode = modelAccessMode;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
    }

    public MLRegisterModelMetaInput(StreamInput in) throws IOException{
        this.name = in.readString();
        this.functionName = in.readEnum(FunctionName.class);
        this.modelGroupId = in.readOptionalString();
        this.version = in.readOptionalString();
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
        this.backendRoles = in.readOptionalStringList();
        if (in.readBoolean()) {
            modelAccessMode = in.readEnum(AccessMode.class);
        }
        this.isAddAllBackendRoles = in.readOptionalBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeString(name);
        out.writeEnum(functionName);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalString(version);
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
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        if (modelAccessMode != null) {
            out.writeBoolean(true);
            out.writeEnum(modelAccessMode);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isAddAllBackendRoles);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MODEL_NAME_FIELD, name);
        builder.field(FUNCTION_NAME_FIELD, functionName);
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
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
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (modelAccessMode != null) {
            builder.field(MODEL_ACCESS_MODE, modelAccessMode);
        }
        if (isAddAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES, isAddAllBackendRoles);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelMetaInput parse(XContentParser parser) throws IOException {
        String name = null;
        FunctionName functionName = null;
        String modelGroupId = null;
        String version = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelState modelState = null;
        Long modelContentSizeInBytes = null;
        String modelContentHashValue = null;
        MLModelConfig modelConfig = null;
        Integer totalChunks = null;
        List<String> backendRoles = null;
        AccessMode modelAccessMode = null;
        Boolean isAddAllBackendRoles = null;

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
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case VERSION_FIELD:
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
                case BACKEND_ROLES_FIELD:
                    backendRoles = new ArrayList<>();
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case MODEL_ACCESS_MODE:
                    modelAccessMode = AccessMode.from(parser.text().toLowerCase(Locale.ROOT));
                    break;
                case ADD_ALL_BACKEND_ROLES:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelMetaInput(name, functionName, modelGroupId, version, description, modelFormat, modelState, modelContentSizeInBytes, modelContentHashValue, modelConfig, totalChunks,  backendRoles, modelAccessMode, isAddAllBackendRoles);
    }

}
