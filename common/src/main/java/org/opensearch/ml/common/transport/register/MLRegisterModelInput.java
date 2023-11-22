/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.register;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.connector.Connector.createConnector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.AccessMode;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
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
    public static final String URL_FIELD = "url";
    public static final String HASH_VALUE_FIELD = "model_content_hash_value";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_CONFIG_FIELD = "model_config";
    public static final String DEPLOY_MODEL_FIELD = "deploy_model";
    public static final String MODEL_NODE_IDS_FIELD = "model_node_ids";
    public static final String CONNECTOR_FIELD = "connector";
    public static final String CONNECTOR_ID_FIELD = "connector_id";
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value";
    public static final String ACCESS_MODE_FIELD = "access_mode";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String ADD_ALL_BACKEND_ROLES_FIELD = "add_all_backend_roles";
    public static final String DOES_VERSION_CREATE_MODEL_GROUP = "does_version_create_model_group";
    private FunctionName functionName;
    private String modelName;
    private String modelGroupId;
    private String version;
    private String description;
    private String url;
    private String hashValue;
    private MLModelFormat modelFormat;
    private MLModelConfig modelConfig;

    private boolean deployModel;
    private String[] modelNodeIds;

    private Connector connector;
    private String connectorId;

    private List<String> backendRoles;
    private Boolean addAllBackendRoles;
    private AccessMode accessMode;
    private Boolean doesVersionCreateModelGroup;

    @Builder(toBuilder = true)
    public MLRegisterModelInput(
        FunctionName functionName,
        String modelName,
        String modelGroupId,
        String version,
        String description,
        String url,
        String hashValue,
        MLModelFormat modelFormat,
        MLModelConfig modelConfig,
        boolean deployModel,
        String[] modelNodeIds,
        Connector connector,
        String connectorId,
        List<String> backendRoles,
        Boolean addAllBackendRoles,
        AccessMode accessMode,
        Boolean doesVersionCreateModelGroup
    ) {
        if (functionName == null) {
            this.functionName = FunctionName.TEXT_EMBEDDING;
        } else {
            this.functionName = functionName;
        }
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
                && functionName != FunctionName.SPARSE_ENCODING) { // The tokenize model doesn't require a model configuration. Currently,
                                                                   // we only support one type of sparse model, which is pretrained, and it
                                                                   // doesn't necessitate a model configuration.
                throw new IllegalArgumentException("model config is null");
            }
        }
        this.modelName = modelName;
        this.modelGroupId = modelGroupId;
        this.version = version;
        this.description = description;
        this.url = url;
        this.hashValue = hashValue;
        this.modelFormat = modelFormat;
        this.modelConfig = modelConfig;
        this.deployModel = deployModel;
        this.modelNodeIds = modelNodeIds;
        this.connector = connector;
        this.connectorId = connectorId;
        this.backendRoles = backendRoles;
        this.addAllBackendRoles = addAllBackendRoles;
        this.accessMode = accessMode;
        this.doesVersionCreateModelGroup = doesVersionCreateModelGroup;
    }

    public MLRegisterModelInput(StreamInput in) throws IOException {
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
            } else {
                this.modelConfig = new TextEmbeddingModelConfig(in);
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
        this.doesVersionCreateModelGroup = in.readOptionalBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
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
        out.writeOptionalBoolean(doesVersionCreateModelGroup);
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
        if (url != null) {
            builder.field(URL_FIELD, url);
        }
        if (hashValue != null) {
            builder.field(HASH_VALUE_FIELD, hashValue);
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
        builder.endObject();
        return builder;
    }

    public static MLRegisterModelInput parse(XContentParser parser, String modelName, String version, boolean deployModel)
        throws IOException {
        FunctionName functionName = null;
        String modelGroupId = null;
        String url = null;
        String hashValue = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        List<String> modelNodeIds = new ArrayList<>();
        Connector connector = null;
        String connectorId = null;
        List<String> backendRoles = new ArrayList<>();
        Boolean addAllBackendRoles = null;
        AccessMode accessMode = null;
        Boolean doesVersionCreateModelGroup = null;

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
                case URL_FIELD:
                    url = parser.text();
                    break;
                case HASH_VALUE_FIELD:
                    hashValue = parser.text();
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
                case ACCESS_MODE_FIELD:
                    accessMode = AccessMode.from(parser.text());
                    break;
                case DOES_VERSION_CREATE_MODEL_GROUP:
                    doesVersionCreateModelGroup = parser.booleanValue();
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
            url,
            hashValue,
            modelFormat,
            modelConfig,
            deployModel,
            modelNodeIds.toArray(new String[0]),
            connector,
            connectorId,
            backendRoles,
            addAllBackendRoles,
            accessMode,
            doesVersionCreateModelGroup
        );
    }

    public static MLRegisterModelInput parse(XContentParser parser, boolean deployModel) throws IOException {
        FunctionName functionName = null;
        String name = null;
        String modelGroupId = null;
        String version = null;
        String url = null;
        String hashValue = null;
        String description = null;
        MLModelFormat modelFormat = null;
        MLModelConfig modelConfig = null;
        List<String> modelNodeIds = new ArrayList<>();
        Connector connector = null;
        String connectorId = null;
        List<String> backendRoles = new ArrayList<>();
        AccessMode accessMode = null;
        Boolean addAllBackendRoles = null;
        Boolean doesVersionCreateModelGroup = null;

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
                case CONNECTOR_FIELD:
                    connector = createConnector(parser);
                    break;
                case HASH_VALUE_FIELD:
                    hashValue = parser.text();
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
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
            url,
            hashValue,
            modelFormat,
            modelConfig,
            deployModel,
            modelNodeIds.toArray(new String[0]),
            connector,
            connectorId,
            backendRoles,
            addAllBackendRoles,
            accessMode,
            doesVersionCreateModelGroup
        );
    }
}
