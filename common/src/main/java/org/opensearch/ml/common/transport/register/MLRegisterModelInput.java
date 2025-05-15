/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.MLModel.allowedInterfaceFieldKeys;
import static org.opensearch.ml.common.connector.Connector.createConnector;
import static org.opensearch.ml.common.utils.StringUtils.filteredParameterMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.CommonValue;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.Guardrails;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.model.RemoteModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;

import lombok.Builder;
import lombok.Data;

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
    public static final String IS_ENABLED_FIELD = "is_enabled";
    public static final String RATE_LIMITER_FIELD = "rate_limiter";
    public static final String URL_FIELD = "url";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_CONFIG_FIELD = "model_config";
    public static final String DEPLOY_SETTING_FIELD = "deploy_setting";
    public static final String DEPLOY_MODEL_FIELD = "deploy_model";
    public static final String MODEL_NODE_IDS_FIELD = "model_node_ids";
    public static final String CONNECTOR_FIELD = "connector";
    public static final String CONNECTOR_ID_FIELD = "connector_id";
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value";
    public static final String ACCESS_MODE_FIELD = "access_mode";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles";
    public static final String DOES_VERSION_CREATE_MODEL_GROUP = "does_version_create_model_group";
    public static final String GUARDRAILS_FIELD = "guardrails";

    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_DOES_VERSION_CREATE_MODEL_GROUP = CommonValue.VERSION_2_11_0;
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_AGENT_FRAMEWORK = CommonValue.VERSION_2_12_0;
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_GUARDRAILS_AND_AUTO_DEPLOY = CommonValue.VERSION_2_13_0;
    public static final Version MINIMAL_SUPPORTED_VERSION_FOR_INTERFACE = CommonValue.VERSION_2_14_0;

    private FunctionName functionName;
    private String modelName;
    private String modelGroupId;
    private String version;
    private String description;
    private Boolean isEnabled;
    private MLRateLimiter rateLimiter;
    private String url;
    private String hashValue;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;
    private MLDeploySetting deploySetting;

    private boolean deployModel;
    private String[] modelNodeIds;

    private Connector connector;
    private String connectorId;

    private List<String> backendRoles;
    private Boolean addAllBackendRoles;
    private AccessMode accessMode;
    private Boolean doesVersionCreateModelGroup;

    private Boolean isHidden;
    private Guardrails guardrails;

    private Map<String, String> modelInterface;
    private String tenantId;

    private static final Map<String, String> MODEL_SPACE_TYPE_MAPPING = Map
        .ofEntries(
            Map.entry("all-distilroberta-v1", "l2"),
            Map.entry("all-MiniLM-L6-v2", "l2"),
            Map.entry("all-MiniLM-L12-v2", "l2"),
            Map.entry("all-mpnet-base-v2", "l2"),
            Map.entry("msmarco-distilbert-base-tas-b", "innerproduct"),
            Map.entry("multi-qa-MiniLM-L6-cos-v1", "l2"),
            Map.entry("multi-qa-mpnet-base-dot-v1", "innerproduct"),
            Map.entry("paraphrase-MiniLM-L3-v2", "cosine"),
            Map.entry("paraphrase-multilingual-MiniLM-L12-v2", "cosine"),
            Map.entry("paraphrase-mpnet-base-v2", "cosine"),
            Map.entry("distiluse-base-multilingual-cased-v1", "cosine")
        );

    private String extractModelName(String fullPath) {
        if (fullPath == null) {
            return null;
        }
        String[] parts = fullPath.split("/");
        return parts[parts.length - 1];
    }

    @Builder(toBuilder = true)
    public MLRegisterModelInput(
        FunctionName functionName,
        String modelName,
        String modelGroupId,
        String version,
        String description,
        Boolean isEnabled,
        MLRateLimiter rateLimiter,
        String url,
        String hashValue,
        MLModelFormat modelFormat,
        MLModelConfig modelConfig,
        MLDeploySetting deploySetting,
        boolean deployModel,
        String[] modelNodeIds,
        Connector connector,
        String connectorId,
        List<String> backendRoles,
        Boolean addAllBackendRoles,
        AccessMode accessMode,
        Boolean doesVersionCreateModelGroup,
        Boolean isHidden,
        Guardrails guardrails,
        Map<String, String> modelInterface,
        String tenantId
    ) {
        this.functionName = Objects.requireNonNullElse(functionName, FunctionName.TEXT_EMBEDDING);
        if (modelName == null) {
            throw new IllegalArgumentException("model name is null");
        }
        if (functionName != FunctionName.REMOTE) {
            if (modelFormat == null) {
                throw new IllegalArgumentException("model format is null");
            }
            if (url != null
                && modelConfig == null
                && functionName != FunctionName.SPARSE_TOKENIZE
                && functionName != FunctionName.SPARSE_ENCODING) { // The tokenize model doesn't require a model
                                                                   // configuration. Currently, we only support one
                                                                   // type of sparse model, which is pretrained, and
                                                                   // it doesn't necessitate a model configuration.
                throw new IllegalArgumentException("model config is null");
            }
        }
        if (modelConfig instanceof TextEmbeddingModelConfig && modelName != null) {
            BaseModelConfig baseModelConfig = (BaseModelConfig) modelConfig;
            String baseModelName = extractModelName(modelName);
            String spaceType = MODEL_SPACE_TYPE_MAPPING.get(baseModelName);
            if (spaceType != null) {
                Map<String, Object> additionalConfig = baseModelConfig.getAdditionalConfig();
                if (additionalConfig == null) {
                    additionalConfig = new HashMap<>();
                    baseModelConfig.setAdditionalConfig(additionalConfig);
                }
                additionalConfig.put("space_type", spaceType);
            }
        }
        this.modelName = modelName;
        this.modelGroupId = modelGroupId;
        this.version = version;
        this.description = description;
        this.isEnabled = isEnabled;
        this.rateLimiter = rateLimiter;
        this.url = url;
        this.hashValue = hashValue;
        this.modelFormat = modelFormat;
        this.modelConfig = modelConfig;
        this.deploySetting = deploySetting;
        this.deployModel = deployModel;
        this.modelNodeIds = modelNodeIds;
        this.connector = connector;
        this.connectorId = connectorId;
        this.backendRoles = backendRoles;
        this.addAllBackendRoles = addAllBackendRoles;
        this.accessMode = accessMode;
        this.doesVersionCreateModelGroup = doesVersionCreateModelGroup;
        this.isHidden = isHidden;
        this.guardrails = guardrails;
        this.modelInterface = modelInterface;
        this.tenantId = tenantId;
    }

    public MLRegisterModelInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        this.functionName = in.readEnum(FunctionName.class);
        this.modelName = in.readString();
        this.modelGroupId = in.readOptionalString();
        this.version = in.readOptionalString();
        this.description = in.readOptionalString();
        this.url = in.readOptionalString();
        this.hashValue = in.readOptionalString();
        if (in.readBoolean()) {
            this.modelFormat = in.readEnum(MLModelFormat.class);
        }
        if (in.readBoolean()) {
            if (this.functionName.equals(FunctionName.METRICS_CORRELATION)) {
                this.modelConfig = new MetricsCorrelationModelConfig(in);
            } else if (this.functionName.equals(FunctionName.QUESTION_ANSWERING)) {
                this.modelConfig = new QuestionAnsweringModelConfig(in);
            } else if (this.functionName.equals(FunctionName.TEXT_EMBEDDING)) {
                this.modelConfig = new TextEmbeddingModelConfig(in);
            } else if (this.functionName.equals(FunctionName.REMOTE)) {
                this.modelConfig = new RemoteModelConfig(in);
            } else {
                this.modelConfig = new BaseModelConfig(in);
            }
        }
        this.deployModel = in.readBoolean();
        this.modelNodeIds = in.readOptionalStringArray();
        if (in.readBoolean()) {
            this.connector = Connector.fromStream(in);
        }
        this.connectorId = in.readOptionalString();
        if (in.readBoolean()) {
            this.backendRoles = in.readOptionalStringList();
        }
        this.addAllBackendRoles = in.readOptionalBoolean();
        if (in.readBoolean()) {
            this.accessMode = in.readEnum(AccessMode.class);
        }
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
                this.guardrails = new Guardrails(in);
            }
            if (in.readBoolean()) {
                this.deploySetting = new MLDeploySetting(in);
            }
        }
        if (streamInputVersion.onOrAfter(MLRegisterModelInput.MINIMAL_SUPPORTED_VERSION_FOR_INTERFACE)) {
            if (in.readBoolean()) {
                this.modelInterface = in.readMap(StreamInput::readString, StreamInput::readString);
            }
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeEnum(functionName);
        out.writeString(modelName);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalString(version);
        out.writeOptionalString(description);
        out.writeOptionalString(url);
        out.writeOptionalString(hashValue);
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
        if (connector != null) {
            out.writeBoolean(true);
            connector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
        if (backendRoles != null) {
            out.writeBoolean(true);
            out.writeOptionalStringCollection(backendRoles);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(addAllBackendRoles);
        if (accessMode != null) {
            out.writeBoolean(true);
            out.writeEnum(accessMode);
        } else {
            out.writeBoolean(false);
        }
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
            if (guardrails != null) {
                out.writeBoolean(true);
                guardrails.writeTo(out);
            } else {
                out.writeBoolean(false);
            }
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
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject();
        builder.field(FUNCTION_NAME_FIELD, functionName);
        builder.field(NAME_FIELD, modelName);
        if (version != null) {
            builder.field(VERSION_FIELD, version);
        }
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (description != null) {
            builder.field(DESCRIPTION_FIELD, description);
        }
        if (isEnabled != null) {
            builder.field(IS_ENABLED_FIELD, isEnabled);
        }
        if (rateLimiter != null) {
            builder.field(RATE_LIMITER_FIELD, rateLimiter);
        }
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (hashValue != null) {
            builder.field(MODEL_CONTENT_HASH_VALUE_FIELD, hashValue);
        }
        if (modelFormat != null) {
            builder.field(MODEL_FORMAT_FIELD, modelFormat);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        if (deploySetting != null) {
            builder.field(DEPLOY_SETTING_FIELD, deploySetting);
        }
        builder.field(DEPLOY_MODEL_FIELD, deployModel);
        if (modelNodeIds != null) {
            builder.field(MODEL_NODE_IDS_FIELD, modelNodeIds);
        }
        if (connector != null) {
            builder.field(CONNECTOR_FIELD, connector);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (backendRoles != null) {
            builder.field(BACKEND_ROLES_FIELD, backendRoles);
        }
        if (addAllBackendRoles != null) {
            builder.field(ADD_ALL_BACKEND_ROLES_FIELD, addAllBackendRoles);
        }
        if (accessMode != null) {
            builder.field(ACCESS_MODE_FIELD, accessMode);
        }
        if (doesVersionCreateModelGroup != null) {
            builder.field(DOES_VERSION_CREATE_MODEL_GROUP, doesVersionCreateModelGroup);
        }
        if (isHidden != null) {
            builder.field(MLModel.IS_HIDDEN_FIELD, isHidden);
        }
        if (guardrails != null) {
            builder.field(GUARDRAILS_FIELD, guardrails);
        }
        if (modelInterface != null) {
            builder.field(MLModel.INTERFACE_FIELD, modelInterface);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID_FIELD, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelInput parse(XContentParser parser, String modelName, String version, boolean deployModel)
        throws IOException {
        FunctionName functionName = null;
        String modelGroupId = null;
        Boolean isEnabled = null;
        MLRateLimiter rateLimiter = null;
        String url = null;
        String hashValue = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        MLDeploySetting deploySetting = null;
        List<String> modelNodeIds = new ArrayList<>();
        Connector connector = null;
        String connectorId = null;
        List<String> backendRoles = new ArrayList<>();
        Boolean addAllBackendRoles = null;
        AccessMode accessMode = null;
        Boolean doesVersionCreateModelGroup = null;
        Boolean isHidden = null;
        Guardrails guardrails = null;
        Map<String, String> modelInterface = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();
            switch (fieldName) {
                case FUNCTION_NAME_FIELD:
                    functionName = FunctionName.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case RATE_LIMITER_FIELD:
                    rateLimiter = MLRateLimiter.parse(parser);
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case MODEL_CONTENT_HASH_VALUE_FIELD:
                    hashValue = parser.text();
                    break;
                case DESCRIPTION_FIELD:
                    description = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_CONFIG_FIELD:
                    if (FunctionName.QUESTION_ANSWERING.equals(functionName)) {
                        modelConfig = QuestionAnsweringModelConfig.parse(parser);
                    } else if (FunctionName.TEXT_EMBEDDING.equals(functionName)) {
                        modelConfig = TextEmbeddingModelConfig.parse(parser);
                    } else if (FunctionName.REMOTE.equals(functionName)) {
                        modelConfig = RemoteModelConfig.parse(parser);
                    } else {
                        modelConfig = BaseModelConfig.parse(parser);
                    }
                    break;
                case DEPLOY_SETTING_FIELD:
                    deploySetting = MLDeploySetting.parse(parser);
                    break;
                case CONNECTOR_FIELD:
                    connector = createConnector(parser);
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case MODEL_NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelNodeIds.add(parser.text());
                    }
                    break;
                case BACKEND_ROLES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    addAllBackendRoles = parser.booleanValue();
                    break;
                case MLModel.IS_HIDDEN_FIELD:
                    isHidden = parser.booleanValue();
                    break;
                case ACCESS_MODE_FIELD:
                    accessMode = AccessMode.from(parser.text());
                    break;
                case DOES_VERSION_CREATE_MODEL_GROUP:
                    doesVersionCreateModelGroup = parser.booleanValue();
                    break;
                case GUARDRAILS_FIELD:
                    guardrails = Guardrails.parse(parser);
                    break;
                case MLModel.INTERFACE_FIELD:
                    modelInterface = filteredParameterMap(parser.map(), allowedInterfaceFieldKeys);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelInput(
            functionName,
            modelName,
            modelGroupId,
            version,
            description,
            isEnabled,
            rateLimiter,
            url,
            hashValue,
            modelFormat,
            modelConfig,
            deploySetting,
            deployModel,
            modelNodeIds.toArray(new String[0]),
            connector,
            connectorId,
            backendRoles,
            addAllBackendRoles,
            accessMode,
            doesVersionCreateModelGroup,
            isHidden,
            guardrails,
            modelInterface,
            tenantId
        );
    }

    public static MLRegisterModelInput parse(XContentParser parser, boolean deployModel) throws IOException {
        FunctionName functionName = null;
        String name = null;
        String modelGroupId = null;
        String version = null;
        Boolean isEnabled = null;
        MLRateLimiter rateLimiter = null;
        String url = null;
        String hashValue = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        MLDeploySetting deploySetting = null;
        List<String> modelNodeIds = new ArrayList<>();
        Connector connector = null;
        String connectorId = null;
        List<String> backendRoles = new ArrayList<>();
        AccessMode accessMode = null;
        Boolean addAllBackendRoles = null;
        Boolean doesVersionCreateModelGroup = null;
        Boolean isHidden = null;
        Guardrails guardrails = null;
        Map<String, String> modelInterface = null;
        String tenantId = null;

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
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case RATE_LIMITER_FIELD:
                    rateLimiter = MLRateLimiter.parse(parser);
                    break;
                case URL_FIELD:
                    url = parser.text();
                    break;
                case CONNECTOR_FIELD:
                    connector = createConnector(parser);
                    break;
                case MODEL_CONTENT_HASH_VALUE_FIELD:
                    hashValue = parser.text();
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case MODEL_FORMAT_FIELD:
                    modelFormat = MLModelFormat.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_CONFIG_FIELD:
                    if (FunctionName.QUESTION_ANSWERING.equals(functionName)) {
                        modelConfig = QuestionAnsweringModelConfig.parse(parser);
                    } else if (FunctionName.TEXT_EMBEDDING.equals(functionName)) {
                        modelConfig = TextEmbeddingModelConfig.parse(parser);
                    } else if (FunctionName.REMOTE.equals(functionName)) {
                        modelConfig = RemoteModelConfig.parse(parser);
                    } else {
                        modelConfig = BaseModelConfig.parse(parser);
                    }
                    break;
                case DEPLOY_SETTING_FIELD:
                    deploySetting = MLDeploySetting.parse(parser);
                    break;
                case MODEL_NODE_IDS_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        modelNodeIds.add(parser.text());
                    }
                    break;
                case BACKEND_ROLES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        backendRoles.add(parser.text());
                    }
                    break;
                case ADD_ALL_BACKEND_ROLES_FIELD:
                    addAllBackendRoles = parser.booleanValue();
                    break;
                case ACCESS_MODE_FIELD:
                    accessMode = AccessMode.from(parser.text());
                    break;
                case DOES_VERSION_CREATE_MODEL_GROUP:
                    doesVersionCreateModelGroup = parser.booleanValue();
                    break;
                case MLModel.IS_HIDDEN_FIELD:
                    isHidden = parser.booleanValue();
                    break;
                case GUARDRAILS_FIELD:
                    guardrails = Guardrails.parse(parser);
                    break;
                case MLModel.INTERFACE_FIELD:
                    modelInterface = filteredParameterMap(parser.map(), allowedInterfaceFieldKeys);
                    break;
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new MLRegisterModelInput(
            functionName,
            name,
            modelGroupId,
            version,
            description,
            isEnabled,
            rateLimiter,
            url,
            hashValue,
            modelFormat,
            modelConfig,
            deploySetting,
            deployModel,
            modelNodeIds.toArray(new String[0]),
            connector,
            connectorId,
            backendRoles,
            addAllBackendRoles,
            accessMode,
            doesVersionCreateModelGroup,
            isHidden,
            guardrails,
            modelInterface,
            tenantId
        );
    }
}
