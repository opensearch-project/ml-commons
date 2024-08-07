/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.upload_chunk;

import lombok.Builder;
import lombok.Data;
import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.ImageEmbeddingModelConfig;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.MLModel.allowedInterfaceFieldKeys;
import static org.opensearch.ml.common.utils.StringUtils.filteredParameterMap;

@Data
public class MLRegisterModelMetaInput implements ToXContentObject, Writeable {

    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String MODEL_NAME_FIELD = "name"; // mandatory
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String IS_ENABLED_FIELD = "is_enabled"; // optional
    public static final String RATE_LIMITER_FIELD = "rate_limiter"; // optional
    public static final String VERSION_FIELD = "version";
    public static final String MODEL_FORMAT_FIELD = "model_format"; // mandatory
    public static final String MODEL_STATE_FIELD = "model_state";
    public static final String MODEL_CONTENT_SIZE_IN_BYTES_FIELD = "model_content_size_in_bytes";
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value"; // mandatory
    public static final String MODEL_CONFIG_FIELD = "model_config"; // mandatory
    public static final String DEPLOY_SETTING_FIELD = "deploy_setting"; // optional
    public static final String TOTAL_CHUNKS_FIELD = "total_chunks"; // mandatory
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String BACKEND_ROLES_FIELD = "backend_roles"; // optional
    public static final String ACCESS_MODE = "access_mode"; // optional
    public static final String ADD_ALL_BACKEND_ROLES = "add_all_backend_roles"; // optional
    public static final String DOES_VERSION_CREATE_MODEL_GROUP = "does_version_create_model_group";

    private FunctionName functionName;
    private String name;

    private String modelGroupId;
    private String description;
    private String version;
    private Boolean isEnabled;
    private MLRateLimiter rateLimiter;
    private MLModelFormat modelFormat;

    private MLModelState modelState;

    private Long modelContentSizeInBytes;
    private String modelContentHashValue;
    private MLModelConfig modelConfig;
    private MLDeploySetting deploySetting;
    private Integer totalChunks;
    private List<String> backendRoles;
    private AccessMode accessMode;
    private Boolean isAddAllBackendRoles;
    private Boolean doesVersionCreateModelGroup;
    private Boolean isHidden;

    private Map<String, String> modelInterface;

    @Builder(toBuilder = true)
    public MLRegisterModelMetaInput(String name, FunctionName functionName, String modelGroupId, String version,
            String description, Boolean isEnabled, MLRateLimiter rateLimiter, MLModelFormat modelFormat,
            MLModelState modelState, Long modelContentSizeInBytes, String modelContentHashValue,
            MLModelConfig modelConfig, MLDeploySetting deploySetting, Integer totalChunks, List<String> backendRoles,
            AccessMode accessMode,
            Boolean isAddAllBackendRoles,
            Boolean doesVersionCreateModelGroup, Boolean isHidden, Map<String, String> modelInterface) {
        if (name == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (functionName == null) {
            this.functionName = FunctionName.TEXT_EMBEDDING;
        } else {
            this.functionName = functionName;
        }
        if (modelFormat == null) {
            throw new IllegalArgumentException("model format is null");
        }
        if (modelContentHashValue == null) {
            throw new IllegalArgumentException("model content hash value is null");
        }
        if (modelConfig == null && functionName != FunctionName.SPARSE_TOKENIZE
                && functionName != FunctionName.SPARSE_ENCODING) { // The tokenize model doesn't require a model
                                                                   // configuration. Currently, we only support one type
                                                                   // of sparse model, which is pretrained, and it
                                                                   // doesn't necessitate a model configuration.
            throw new IllegalArgumentException("model config is null");
        }
        if (totalChunks == null) {
            throw new IllegalArgumentException("total chunks field is null");
        }
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.version = version;
        this.description = description;
        this.isEnabled = isEnabled;
        this.rateLimiter = rateLimiter;
        this.modelFormat = modelFormat;
        this.modelState = modelState;
        this.modelContentSizeInBytes = modelContentSizeInBytes;
        this.modelContentHashValue = modelContentHashValue;
        this.modelConfig = modelConfig;
        this.deploySetting = deploySetting;
        this.totalChunks = totalChunks;
        this.backendRoles = backendRoles;
        this.accessMode = accessMode;
        this.isAddAllBackendRoles = isAddAllBackendRoles;
        this.doesVersionCreateModelGroup = doesVersionCreateModelGroup;
        this.isHidden = isHidden;
        this.modelInterface = modelInterface;
    }

    public MLRegisterModelMetaInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
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
            if (this.functionName.equals(FunctionName.QUESTION_ANSWERING)) {
                this.modelConfig = new QuestionAnsweringModelConfig(in);
            } else if(this.functionName.equals(FunctionName.IMAGE_EMBEDDING)) {
                this.modelConfig = new ImageEmbeddingModelConfig(in);
            } else {
                this.modelConfig = new TextEmbeddingModelConfig(in);
            }
        }
        this.totalChunks = in.readInt();
        this.backendRoles = in.readOptionalStringList();
        if (in.readBoolean()) {
            accessMode = in.readEnum(AccessMode.class);
        }
        this.isAddAllBackendRoles = in.readOptionalBoolean();
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_DOES_VERSION_CREATE_MODEL_GROUP)) {
            this.doesVersionCreateModelGroup = in.readOptionalBoolean();
        }
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            this.isEnabled = in.readOptionalBoolean();
            if (in.readBoolean()) {
                this.rateLimiter = new MLRateLimiter(in);
            }
            this.isHidden = in.readOptionalBoolean();
        }
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_GUARDRAILS_AND_AUTO_DEPLOY)) {
            if (in.readBoolean()) {
                this.deploySetting = new MLDeploySetting(in);
            }
        }
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_INTERFACE)) {
            if (in.readBoolean()) {
                this.modelInterface = in.readMap(StreamInput::readString, StreamInput::readString);
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
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
        if (accessMode != null) {
            out.writeBoolean(true);
            out.writeEnum(accessMode);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isAddAllBackendRoles);
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_DOES_VERSION_CREATE_MODEL_GROUP)) {
            out.writeOptionalBoolean(doesVersionCreateModelGroup);
        }
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK)) {
            out.writeOptionalBoolean(isEnabled);
            if (rateLimiter != null) {
                out.writeBoolean(true);
                rateLimiter.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
            out.writeOptionalBoolean(isHidden);
        }
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_GUARDRAILS_AND_AUTO_DEPLOY)) {
            if (deploySetting != null) {
                out.writeBoolean(true);
                deploySetting.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
        }
        if (streamOutputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_INTERFACE)) {
            if (modelInterface != null) {
                out.writeBoolean(true);
                out.writeMap(modelInterface, StreamOutput::writeString, StreamOutput::writeString);
            } else {
                out.writeBoolean(false);
            }
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.field(MLModel.MODEL_NAME_FIELD, name);
        builder.field(MLModel.FUNCTION_NAME_FIELD, functionName);
        if (modelGroupId != null) {
            builder.field(MLModel.MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
        if (description != null) {
            builder.field(MLModel.DESCRIPTION_FIELD, description);
        }
        if (isEnabled != null) {
            builder.field(IS_ENABLED_FIELD, isEnabled);
        }
        if (rateLimiter != null) {
            builder.field(RATE_LIMITER_FIELD, rateLimiter);
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
        if (deploySetting != null) {
            builder.field(DEPLOY_SETTING_FIELD, deploySetting);
        }
        if (backendRoles != null && backendRoles.size() > 0) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (accessMode != null) {
            builder.field(ACCESS_MODE, accessMode);
        }
        if (isAddAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES, isAddAllBackendRoles);
        }
        if (doesVersionCreateModelGroup != null) {
            builder.field(DOES_VERSION_CREATE_MODEL_GROUP, doesVersionCreateModelGroup);
        }
        if (isHidden != null) {
            builder.field(MLModel.IS_HIDDEN_FIELD, isHidden);
        }
        if (modelInterface != null) {
            builder.field(MLModel.INTERFACE_FIELD, modelInterface);
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
        Boolean isEnabled = null;
        MLRateLimiter rateLimiter = null;
        MLModelFormat modelFormat = null;
        MLModelState modelState = null;
        Long modelContentSizeInBytes = null;
        String modelContentHashValue = null;
        MLModelConfig modelConfig = null;
        MLDeploySetting deploySetting = null;
        Integer totalChunks = null;
        List<String> backendRoles = null;
        AccessMode accessMode = null;
        Boolean isAddAllBackendRoles = null;
        Boolean doesVersionCreateModelGroup = null;
        Boolean isHidden = null;
        Map<String, String> modelInterface = null;

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
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case RATE_LIMITER_FIELD:
                    rateLimiter = MLRateLimiter.parse(parser);
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
                    if (FunctionName.QUESTION_ANSWERING.equals(functionName)) {
                        modelConfig = QuestionAnsweringModelConfig.parse(parser);
                    } else if(FunctionName.IMAGE_EMBEDDING.equals(functionName)) {
                        modelConfig = ImageEmbeddingModelConfig.parse(parser);
                    } else {
                        modelConfig = TextEmbeddingModelConfig.parse(parser);
                    }
                    break;
                case DEPLOY_SETTING_FIELD:
                    deploySetting = MLDeploySetting.parse(parser);
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
                case ACCESS_MODE:
                    accessMode = AccessMode.from(parser.text().toLowerCase(Locale.ROOT));
                    break;
                case ADD_ALL_BACKEND_ROLES:
                    isAddAllBackendRoles = parser.booleanValue();
                    break;
                case DOES_VERSION_CREATE_MODEL_GROUP:
                    doesVersionCreateModelGroup = parser.booleanValue();
                    break;
                case MLModel.IS_HIDDEN_FIELD:
                    isHidden = parser.booleanValue();
                    break;
                case MLModel.INTERFACE_FIELD:
                    modelInterface = filteredParameterMap(parser.map(), allowedInterfaceFieldKeys);
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelMetaInput(name, functionName, modelGroupId, version, description, isEnabled,
                rateLimiter, modelFormat, modelState, modelContentSizeInBytes, modelContentHashValue, modelConfig,
                deploySetting, totalChunks, backendRoles, accessMode, isAddAllBackendRoles, doesVersionCreateModelGroup,
                isHidden, modelInterface);
    }

}
