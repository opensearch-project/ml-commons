/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.model;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.MLModel.allowedInterfaceFieldKeys;
import static org.opensearch.ml.common.utils.StringUtils.filteredParameterMap;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.opensearch.Version;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.Guardrails;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.transport.connector.MLCreateConnectorInput;
import org.opensearch.ml.common.transport.register.MLRegisterModelInput;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
public class MLUpdateModelInput implements ToXContentObject, Writeable {

    public static final String MODEL_ID_FIELD = "model_id"; // passively set when passing url to rest API
    public static final String DESCRIPTION_FIELD = "description"; // optional
    public static final String MODEL_VERSION_FIELD = "model_version"; // passively set when register model to a new
                                                                      // model group
    public static final String MODEL_NAME_FIELD = "name"; // optional
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id"; // optional
    public static final String IS_ENABLED_FIELD = "is_enabled"; // optional
    public static final String RATE_LIMITER_FIELD = "rate_limiter"; // optional
    public static final String MODEL_CONFIG_FIELD = "model_config"; // optional
    public static final String DEPLOY_SETTING_FIELD = "deploy_setting"; // optional
    public static final String UPDATED_CONNECTOR_FIELD = "updated_connector"; // passively set when updating the
                                                                              // internal connector
    public static final String CONNECTOR_ID_FIELD = "connector_id"; // optional
    public static final String CONNECTOR_FIELD = "connector"; // optional
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time"; // passively set when sending update
                                                                              // request
    public static final String GUARDRAILS_FIELD = "guardrails";

    @Getter
    private String modelId;
    private String description;
    private String version;
    private String name;
    private String modelGroupId;
    private Boolean isEnabled;
    private MLRateLimiter rateLimiter;
    private MLModelConfig modelConfig;
    private MLDeploySetting deploySetting;
    private Connector updatedConnector;
    private String connectorId;
    private MLCreateConnectorInput connector;
    private Instant lastUpdateTime;
    private Guardrails guardrails;
    private String tenantId;

    private Map<String, String> modelInterface;

    @Builder(toBuilder = true)
    public MLUpdateModelInput(
        String modelId,
        String description,
        String version,
        String name,
        String modelGroupId,
        Boolean isEnabled,
        MLRateLimiter rateLimiter,
        MLModelConfig modelConfig,
        MLDeploySetting deploySetting,
        Connector updatedConnector,
        String connectorId,
        MLCreateConnectorInput connector,
        Instant lastUpdateTime,
        Guardrails guardrails,
        Map<String, String> modelInterface,
        String tenantId
    ) {
        this.modelId = modelId;
        this.description = description;
        this.version = version;
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.isEnabled = isEnabled;
        this.rateLimiter = rateLimiter;
        this.modelConfig = modelConfig;
        this.deploySetting = deploySetting;
        this.updatedConnector = updatedConnector;
        this.connectorId = connectorId;
        this.connector = connector;
        this.lastUpdateTime = lastUpdateTime;
        this.guardrails = guardrails;
        this.modelInterface = modelInterface;
        this.tenantId = tenantId;
    }

    public MLUpdateModelInput(StreamInput in) throws IOException {
        Version streamInputVersion = in.getVersion();
        modelId = in.readString();
        description = in.readOptionalString();
        version = in.readOptionalString();
        name = in.readOptionalString();
        modelGroupId = in.readOptionalString();
        isEnabled = in.readOptionalBoolean();
        if (in.readBoolean()) {
            rateLimiter = new MLRateLimiter(in);
        }
        if (in.readBoolean()) {
            modelConfig = new BaseModelConfig(in);
        }
        if (in.readBoolean()) {
            updatedConnector = Connector.fromStream(in);
        }
        connectorId = in.readOptionalString();
        if (in.readBoolean()) {
            connector = new MLCreateConnectorInput(in);
        }
        lastUpdateTime = in.readOptionalInstant();
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
                modelInterface = in.readMap(StreamInput::readString, StreamInput::readString);
            }
        }
        this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? in.readOptionalString() : null;
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
        if (rateLimiter != null) {
            builder.field(RATE_LIMITER_FIELD, rateLimiter);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        if (deploySetting != null) {
            builder.field(DEPLOY_SETTING_FIELD, deploySetting);
        }
        // Notice that we serialize the updatedConnector to the connector field, in order to be compatible with original internal connector
        // field format.
        if (updatedConnector != null) {
            builder.field(CONNECTOR_FIELD, updatedConnector);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
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

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
        out.writeString(modelId);
        out.writeOptionalString(description);
        out.writeOptionalString(version);
        out.writeOptionalString(name);
        out.writeOptionalString(modelGroupId);
        out.writeOptionalBoolean(isEnabled);
        if (rateLimiter != null) {
            out.writeBoolean(true);
            rateLimiter.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (updatedConnector != null) {
            out.writeBoolean(true);
            updatedConnector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
        if (connector != null) {
            out.writeBoolean(true);
            connector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(lastUpdateTime);
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

    public static MLUpdateModelInput parse(XContentParser parser) throws IOException {
        String modelId = null;
        String description = null;
        String version = null;
        String name = null;
        String modelGroupId = null;
        Boolean isEnabled = null;
        MLRateLimiter rateLimiter = null;
        MLModelConfig modelConfig = null;
        MLDeploySetting deploySetting = null;
        Connector updatedConnector = null;
        String connectorId = null;
        MLCreateConnectorInput connector = null;
        Instant lastUpdateTime = null;
        Guardrails guardrails = null;
        Map<String, String> modelInterface = null;
        String tenantId = null;

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
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case RATE_LIMITER_FIELD:
                    rateLimiter = MLRateLimiter.parse(parser);
                    break;
                case MODEL_CONFIG_FIELD:
                    modelConfig = BaseModelConfig.parse(parser);
                    break;
                case DEPLOY_SETTING_FIELD:
                    deploySetting = MLDeploySetting.parse(parser);
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case CONNECTOR_FIELD:
                    connector = MLCreateConnectorInput.parse(parser, true);
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
        // Model ID can only be set through RestRequest. Model version can only be set
        // automatically.
        return new MLUpdateModelInput(
            modelId,
            description,
            version,
            name,
            modelGroupId,
            isEnabled,
            rateLimiter,
            modelConfig,
            deploySetting,
            updatedConnector,
            connectorId,
            connector,
            lastUpdateTime,
            guardrails,
            modelInterface,
            tenantId
        );
    }
}
