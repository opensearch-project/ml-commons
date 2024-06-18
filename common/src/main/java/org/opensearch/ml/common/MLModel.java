/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.model.Guardrails;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID;
import static org.opensearch.ml.common.CommonValue.USER;
import static org.opensearch.ml.common.connector.Connector.createConnector;
import static org.opensearch.ml.common.utils.StringUtils.filteredParameterMap;

@Getter
public class MLModel implements ToXContentObject {
    @Deprecated
    public static final String ALGORITHM_FIELD = "algorithm";
    public static final String FUNCTION_NAME_FIELD = "function_name";
    public static final String MODEL_NAME_FIELD = "name";
    public static final String MODEL_GROUP_ID_FIELD = "model_group_id";
    // We use int type for version in first release 1.3. In 2.4, we changed to
    // use String type for version. Keep this old version field for old models.
    public static final String OLD_MODEL_VERSION_FIELD = "version";
    public static final String MODEL_VERSION_FIELD = "model_version";
    public static final String OLD_MODEL_CONTENT_FIELD = "content";
    public static final String MODEL_CONTENT_FIELD = "model_content";

    public static final String DESCRIPTION_FIELD = "description";
    public static final String MODEL_FORMAT_FIELD = "model_format";
    public static final String MODEL_STATE_FIELD = "model_state";
    public static final String MODEL_CONTENT_SIZE_IN_BYTES_FIELD = "model_content_size_in_bytes";
    // SHA256 hash value of model content.
    public static final String MODEL_CONTENT_HASH_VALUE_FIELD = "model_content_hash_value";

    // Model level quota and throttling control
    public static final String IS_ENABLED_FIELD = "is_enabled";
    public static final String RATE_LIMITER_FIELD = "rate_limiter";
    public static final String IS_CONTROLLER_ENABLED_FIELD = "is_controller_enabled";
    public static final String MODEL_CONFIG_FIELD = "model_config";
    public static final String DEPLOY_SETTING_FIELD = "deploy_setting"; // optional
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    @Deprecated
    public static final String LAST_UPLOADED_TIME_FIELD = "last_uploaded_time";
    @Deprecated
    public static final String LAST_LOADED_TIME_FIELD = "last_loaded_time";
    @Deprecated
    public static final String LAST_UNLOADED_TIME_FIELD = "last_unloaded_time";
    public static final String LAST_REGISTERED_TIME_FIELD = "last_registered_time";
    public static final String LAST_DEPLOYED_TIME_FIELD = "last_deployed_time";
    public static final String LAST_UNDEPLOYED_TIME_FIELD = "last_undeployed_time";

    public static final String MODEL_ID_FIELD = "model_id";
    // auto redploy retry times for this model.
    public static final String AUTO_REDEPLOY_RETRY_TIMES_FIELD = "auto_redeploy_retry_times";
    public static final String CHUNK_NUMBER_FIELD = "chunk_number";
    public static final String TOTAL_CHUNKS_FIELD = "total_chunks";
    public static final String PLANNING_WORKER_NODE_COUNT_FIELD = "planning_worker_node_count";
    public static final String CURRENT_WORKER_NODE_COUNT_FIELD = "current_worker_node_count";
    public static final String PLANNING_WORKER_NODES_FIELD = "planning_worker_nodes";
    public static final String DEPLOY_TO_ALL_NODES_FIELD = "deploy_to_all_nodes";

    public static final String IS_HIDDEN_FIELD = "is_hidden";
    public static final String CONNECTOR_FIELD = "connector";
    public static final String CONNECTOR_ID_FIELD = "connector_id";
    public static final String GUARDRAILS_FIELD = "guardrails";
    public static final String INTERFACE_FIELD = "interface";

    public static final Set<String> allowedInterfaceFieldKeys = new HashSet<>(Arrays.asList("input", "output"));

    private String name;
    private String modelGroupId;
    private FunctionName algorithm;
    private String version;
    private String content;
    private User user;

    @Setter
    private String description;
    private MLModelFormat modelFormat;
    private MLModelState modelState;
    private Long modelContentSizeInBytes;
    private String modelContentHash;
    private MLModelConfig modelConfig;
    private MLDeploySetting deploySetting;
    private Boolean isEnabled;
    private Boolean isControllerEnabled;
    private MLRateLimiter rateLimiter;
    private Instant createdTime;
    private Instant lastUpdateTime;
    private Instant lastRegisteredTime;
    private Instant lastDeployedTime;
    private Instant lastUndeployedTime;

    @Setter
    private String modelId; // model chunk doc only

    private Integer autoRedeployRetryTimes;
    private Integer chunkNumber; // model chunk doc only
    private Integer totalChunks; // model chunk doc only
    private Integer planningWorkerNodeCount; // plan to deploy model to how many nodes
    private Integer currentWorkerNodeCount; // model is deployed to how many nodes
    private String[] planningWorkerNodes; // plan to deploy model to these nodes
    private boolean deployToAllNodes;

    // is domain manager creates any special hidden model in the cluster this status
    // will be true. Otherwise,
    // False by default
    private Boolean isHidden;
    @Setter
    private Connector connector;
    private String connectorId;
    private Guardrails guardrails;
    private String tenantId;

    /**
     * Model interface is a map that contains the input and output fields of the model, with JSON schema as the value.
     * Sample model interface:
     * {
     *   "interface": {
     *     "input": {
     *       "properties": {
     *         "parameters": {
     *           "properties": {
     *             "messages": {
     *               "type": "string",
     *               "description": "This is a test description field"
     *             }
     *           }
     *         }
     *       }
     *     },
     *     "output": {
     *       "properties": {
     *         "inference_results": {
     *           "type": "array",
     *           "description": "This is a test description field"
     *         }
     *       }
     *     }
     *   }
     * }
     */
    private Map<String, String> modelInterface;

    @Builder(toBuilder = true)
    public MLModel(String name,
            String modelGroupId,
            FunctionName algorithm,
            String version,
            String content,
            User user,
            String description,
            MLModelFormat modelFormat,
            MLModelState modelState,
            Long modelContentSizeInBytes,
            String modelContentHash,
            Boolean isEnabled,
            Boolean isControllerEnabled,
            MLRateLimiter rateLimiter,
            MLModelConfig modelConfig,
            MLDeploySetting deploySetting,
            Instant createdTime,
            Instant lastUpdateTime,
            Instant lastRegisteredTime,
            Instant lastDeployedTime,
            Instant lastUndeployedTime,
            Integer autoRedeployRetryTimes,
            String modelId, Integer chunkNumber,
            Integer totalChunks,
            Integer planningWorkerNodeCount,
            Integer currentWorkerNodeCount,
            String[] planningWorkerNodes,
            boolean deployToAllNodes,
            Boolean isHidden,
            Connector connector,
            String connectorId,
            Guardrails guardrails,
            Map<String, String> modelInterface,
           String tenantId) {
        this.name = name;
        this.modelGroupId = modelGroupId;
        this.algorithm = algorithm;
        this.version = version;
        this.content = content;
        this.user = user;
        this.description = description;
        this.modelFormat = modelFormat;
        this.modelState = modelState;
        this.modelContentSizeInBytes = modelContentSizeInBytes;
        this.modelContentHash = modelContentHash;
        this.isEnabled = isEnabled;
        this.isControllerEnabled = isControllerEnabled;
        this.rateLimiter = rateLimiter;
        this.modelConfig = modelConfig;
        this.deploySetting = deploySetting;
        this.createdTime = createdTime;
        this.lastUpdateTime = lastUpdateTime;
        this.lastRegisteredTime = lastRegisteredTime;
        this.lastDeployedTime = lastDeployedTime;
        this.lastUndeployedTime = lastUndeployedTime;
        this.modelId = modelId;
        this.autoRedeployRetryTimes = autoRedeployRetryTimes;
        this.chunkNumber = chunkNumber;
        this.totalChunks = totalChunks;
        this.planningWorkerNodeCount = planningWorkerNodeCount;
        this.currentWorkerNodeCount = currentWorkerNodeCount;
        this.planningWorkerNodes = planningWorkerNodes;
        this.deployToAllNodes = deployToAllNodes;
        this.isHidden = isHidden;
        this.connector = connector;
        this.connectorId = connectorId;
        this.guardrails = guardrails;
        this.modelInterface = modelInterface;
        this.tenantId = tenantId;
    }

    public MLModel(StreamInput input) throws IOException {
        name = input.readOptionalString();
        algorithm = input.readEnum(FunctionName.class);
        version = input.readString();
        content = input.readOptionalString();
        if (input.readBoolean()) {
            this.user = new User(input);
        } else {
            user = null;
        }
        if (input.available() > 0) {
            description = input.readOptionalString();
            if (input.readBoolean()) {
                modelFormat = input.readEnum(MLModelFormat.class);
            }
            if (input.readBoolean()) {
                modelState = input.readEnum(MLModelState.class);
            }
            modelContentSizeInBytes = input.readOptionalLong();
            modelContentHash = input.readOptionalString();
            if (input.readBoolean()) {
                if (algorithm.equals(FunctionName.METRICS_CORRELATION)) {
                    modelConfig = new MetricsCorrelationModelConfig(input);
                } else if (algorithm.equals(FunctionName.QUESTION_ANSWERING)) {
                    modelConfig = new QuestionAnsweringModelConfig(input);
                } else {
                    modelConfig = new TextEmbeddingModelConfig(input);
                }
            }
            if (input.readBoolean()) {
                this.deploySetting = new MLDeploySetting(input);
            }
            isEnabled = input.readOptionalBoolean();
            isControllerEnabled = input.readOptionalBoolean();
            if (input.readBoolean()) {
                rateLimiter = new MLRateLimiter(input);
            }
            createdTime = input.readOptionalInstant();
            lastUpdateTime = input.readOptionalInstant();
            lastRegisteredTime = input.readOptionalInstant();
            lastDeployedTime = input.readOptionalInstant();
            lastUndeployedTime = input.readOptionalInstant();
            modelId = input.readOptionalString();
            autoRedeployRetryTimes = input.readOptionalInt();
            chunkNumber = input.readOptionalInt();
            totalChunks = input.readOptionalInt();
            planningWorkerNodeCount = input.readOptionalInt();
            currentWorkerNodeCount = input.readOptionalInt();
            planningWorkerNodes = input.readOptionalStringArray();
            deployToAllNodes = input.readBoolean();
            isHidden = input.readOptionalBoolean();
            modelGroupId = input.readOptionalString();
            if (input.readBoolean()) {
                connector = Connector.fromStream(input);
            }
            connectorId = input.readOptionalString();
            if (input.readBoolean()) {
                this.guardrails = new Guardrails(input);
            }
            if (input.readBoolean()) {
                modelInterface = input.readMap(StreamInput::readString, StreamInput::readString);
            }
            tenantId = input.readOptionalString();
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        out.writeOptionalString(name);
        out.writeEnum(algorithm);
        out.writeString(version);
        out.writeOptionalString(content);
        if (user != null) {
            out.writeBoolean(true); // user exists
            user.writeTo(out);
        } else {
            out.writeBoolean(false); // user does not exist
        }
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
        out.writeOptionalString(modelContentHash);
        if (modelConfig != null) {
            out.writeBoolean(true);
            modelConfig.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (deploySetting != null) {
            out.writeBoolean(true);
            deploySetting.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalBoolean(isEnabled);
        out.writeOptionalBoolean(isControllerEnabled);
        if (rateLimiter != null) {
            out.writeBoolean(true);
            rateLimiter.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalInstant(createdTime);
        out.writeOptionalInstant(lastUpdateTime);
        out.writeOptionalInstant(lastRegisteredTime);
        out.writeOptionalInstant(lastDeployedTime);
        out.writeOptionalInstant(lastUndeployedTime);
        out.writeOptionalString(modelId);
        out.writeOptionalInt(autoRedeployRetryTimes);
        out.writeOptionalInt(chunkNumber);
        out.writeOptionalInt(totalChunks);
        out.writeOptionalInt(planningWorkerNodeCount);
        out.writeOptionalInt(currentWorkerNodeCount);
        out.writeOptionalStringArray(planningWorkerNodes);
        out.writeBoolean(deployToAllNodes);
        out.writeOptionalBoolean(isHidden);
        out.writeOptionalString(modelGroupId);
        if (connector != null) {
            out.writeBoolean(true);
            connector.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(connectorId);
        if (guardrails != null) {
            out.writeBoolean(true);
            guardrails.writeTo(out);
        } else {
            out.writeBoolean(false);
        }
        if (modelInterface != null) {
            out.writeBoolean(true);
            out.writeMap(modelInterface, StreamOutput::writeString, StreamOutput::writeString);
        } else {
            out.writeBoolean(false);
        }
        out.writeOptionalString(tenantId);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        if (name != null) {
            builder.field(MODEL_NAME_FIELD, name);
        }
        if (modelGroupId != null) {
            builder.field(MODEL_GROUP_ID_FIELD, modelGroupId);
        }
        if (algorithm != null) {
            builder.field(ALGORITHM_FIELD, algorithm);
        }
        if (version != null) {
            builder.field(MODEL_VERSION_FIELD, version);
        }
        if (content != null) {
            builder.field(MODEL_CONTENT_FIELD, content);
        }
        if (user != null) {
            builder.field(USER, user);
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
            builder.field(MODEL_CONTENT_HASH_VALUE_FIELD, modelContentHash);
        }
        if (modelConfig != null) {
            builder.field(MODEL_CONFIG_FIELD, modelConfig);
        }
        if (deploySetting != null) {
            builder.field(DEPLOY_SETTING_FIELD, deploySetting);
        }
        if (isEnabled != null) {
            builder.field(IS_ENABLED_FIELD, isEnabled);
        }
        if (isControllerEnabled != null) {
            builder.field(IS_CONTROLLER_ENABLED_FIELD, isControllerEnabled);
        }
        if (rateLimiter != null) {
            builder.field(RATE_LIMITER_FIELD, rateLimiter);
        }
        if (createdTime != null) {
            builder.field(CREATED_TIME_FIELD, createdTime.toEpochMilli());
        }
        if (lastUpdateTime != null) {
            builder.field(LAST_UPDATED_TIME_FIELD, lastUpdateTime.toEpochMilli());
        }
        if (lastRegisteredTime != null) {
            builder.field(LAST_REGISTERED_TIME_FIELD, lastRegisteredTime.toEpochMilli());
        }
        if (lastDeployedTime != null) {
            builder.field(LAST_DEPLOYED_TIME_FIELD, lastDeployedTime.toEpochMilli());
        }
        if (lastUndeployedTime != null) {
            builder.field(LAST_UNDEPLOYED_TIME_FIELD, lastUndeployedTime.toEpochMilli());
        }
        if (modelId != null) {
            builder.field(MODEL_ID_FIELD, modelId);
        }
        if (autoRedeployRetryTimes != null) {
            builder.field(AUTO_REDEPLOY_RETRY_TIMES_FIELD, autoRedeployRetryTimes);
        }
        if (chunkNumber != null) {
            builder.field(CHUNK_NUMBER_FIELD, chunkNumber);
        }
        if (totalChunks != null) {
            builder.field(TOTAL_CHUNKS_FIELD, totalChunks);
        }
        if (planningWorkerNodeCount != null) {
            builder.field(PLANNING_WORKER_NODE_COUNT_FIELD, planningWorkerNodeCount);
        }
        if (currentWorkerNodeCount != null) {
            builder.field(CURRENT_WORKER_NODE_COUNT_FIELD, currentWorkerNodeCount);
        }
        if (planningWorkerNodes != null && planningWorkerNodes.length > 0) {
            builder.field(PLANNING_WORKER_NODES_FIELD, planningWorkerNodes);
        }
        if (deployToAllNodes) {
            builder.field(DEPLOY_TO_ALL_NODES_FIELD, deployToAllNodes);
        }
        if (isHidden != null) {
            builder.field(MLModel.IS_HIDDEN_FIELD, isHidden);
        }
        if (connector != null) {
            builder.field(CONNECTOR_FIELD, connector);
        }
        if (connectorId != null) {
            builder.field(CONNECTOR_ID_FIELD, connectorId);
        }
        if (guardrails != null) {
            builder.field(GUARDRAILS_FIELD, guardrails);
        }
        if (modelInterface != null) {
            builder.field(INTERFACE_FIELD, modelInterface);
        }
        if (tenantId != null) {
            builder.field(TENANT_ID, tenantId);
        }
        builder.endObject();
        return builder;
    }

    public static MLModel parse(XContentParser parser, String algorithmName) throws IOException {
        String name = null;
        String modelGroupId = null;
        FunctionName algorithm = null;
        String version = null;
        Integer oldVersion = null;
        String content = null;
        String oldContent = null;
        User user = null;

        String description = null;
        MLModelFormat modelFormat = null;
        MLModelState modelState = null;
        Long modelContentSizeInBytes = null;
        String modelContentHash = null;
        MLModelConfig modelConfig = null;
        MLDeploySetting deploySetting = null;
        Boolean isEnabled = null;
        Boolean isControllerEnabled = null;
        MLRateLimiter rateLimiter = null;
        Instant createdTime = null;
        Instant lastUpdateTime = null;
        Instant lastUploadedTime = null;
        Instant lastLoadedTime = null;
        Instant lastUnloadedTime = null;
        Instant lastRegisteredTime = null;
        Instant lastDeployedTime = null;
        Instant lastUndeployedTime = null;
        String modelId = null;
        Integer autoRedeployRetryTimes = null;
        Integer chunkNumber = null;
        Integer totalChunks = null;
        Integer planningWorkerNodeCount = null;
        Integer currentWorkerNodeCount = null;
        List<String> planningWorkerNodes = new ArrayList<>();
        boolean deployToAllNodes = false;
        boolean isHidden = false;
        Connector connector = null;
        String connectorId = null;
        Guardrails guardrails = null;
        Map<String, String> modelInterface = null;
        String tenantId = null;

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case MODEL_NAME_FIELD:
                    name = parser.text();
                    break;
                case MODEL_GROUP_ID_FIELD:
                    modelGroupId = parser.text();
                    break;
                case MODEL_CONTENT_FIELD:
                    content = parser.text();
                    break;
                case OLD_MODEL_CONTENT_FIELD:
                    oldContent = parser.text();
                    break;
                case MODEL_VERSION_FIELD:
                    version = parser.text();
                    break;
                case OLD_MODEL_VERSION_FIELD:
                    oldVersion = parser.intValue(false);
                    break;
                case CHUNK_NUMBER_FIELD:
                    chunkNumber = parser.intValue(false);
                    break;
                case TOTAL_CHUNKS_FIELD:
                    totalChunks = parser.intValue(false);
                    break;
                case USER:
                    user = User.parse(parser);
                    break;
                case ALGORITHM_FIELD:
                case FUNCTION_NAME_FIELD:
                    algorithm = FunctionName.from(parser.text().toUpperCase(Locale.ROOT));
                    break;
                case MODEL_ID_FIELD:
                    modelId = parser.text();
                    break;
                case AUTO_REDEPLOY_RETRY_TIMES_FIELD:
                    autoRedeployRetryTimes = parser.intValue();
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
                    modelContentHash = parser.text();
                    break;
                case MODEL_CONFIG_FIELD:
                    if (FunctionName.METRICS_CORRELATION.name().equals(algorithmName)) {
                        modelConfig = MetricsCorrelationModelConfig.parse(parser);
                    } else if (FunctionName.QUESTION_ANSWERING.name().equals(algorithmName)) {
                        modelConfig = QuestionAnsweringModelConfig.parse(parser);
                    } else {
                        modelConfig = TextEmbeddingModelConfig.parse(parser);
                    }
                    break;
                case DEPLOY_SETTING_FIELD:
                    deploySetting = MLDeploySetting.parse(parser);
                    break;
                case IS_ENABLED_FIELD:
                    isEnabled = parser.booleanValue();
                    break;
                case IS_CONTROLLER_ENABLED_FIELD:
                    isControllerEnabled = parser.booleanValue();
                    break;
                case RATE_LIMITER_FIELD:
                    rateLimiter = MLRateLimiter.parse(parser);
                    break;
                case PLANNING_WORKER_NODE_COUNT_FIELD:
                    planningWorkerNodeCount = parser.intValue();
                    break;
                case CURRENT_WORKER_NODE_COUNT_FIELD:
                    currentWorkerNodeCount = parser.intValue();
                    break;
                case PLANNING_WORKER_NODES_FIELD:
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        planningWorkerNodes.add(parser.text());
                    }
                    break;
                case DEPLOY_TO_ALL_NODES_FIELD:
                    deployToAllNodes = parser.booleanValue();
                    break;
                case IS_HIDDEN_FIELD:
                    isHidden = parser.booleanValue();
                    break;
                case CONNECTOR_FIELD:
                    connector = createConnector(parser);
                    break;
                case CONNECTOR_ID_FIELD:
                    connectorId = parser.text();
                    break;
                case CREATED_TIME_FIELD:
                    createdTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPDATED_TIME_FIELD:
                    lastUpdateTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UPLOADED_TIME_FIELD:
                    lastUploadedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_LOADED_TIME_FIELD:
                    lastLoadedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UNLOADED_TIME_FIELD:
                    lastUnloadedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_REGISTERED_TIME_FIELD:
                    lastRegisteredTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_DEPLOYED_TIME_FIELD:
                    lastDeployedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case LAST_UNDEPLOYED_TIME_FIELD:
                    lastUndeployedTime = Instant.ofEpochMilli(parser.longValue());
                    break;
                case GUARDRAILS_FIELD:
                    guardrails = Guardrails.parse(parser);
                    break;
                case INTERFACE_FIELD:
                    modelInterface = filteredParameterMap(parser.map(), allowedInterfaceFieldKeys);
                    break;
                case TENANT_ID:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLModel.builder()
                .name(name)
                .modelGroupId(modelGroupId)
                .algorithm(algorithm)
                .version(version == null ? oldVersion + "" : version)
                .content(content == null ? oldContent : content)
                .user(user)
                .description(description)
                .modelFormat(modelFormat)
                .modelState(modelState)
                .modelContentSizeInBytes(modelContentSizeInBytes)
                .modelContentHash(modelContentHash)
                .modelConfig(modelConfig)
                .deploySetting(deploySetting)
                .isEnabled(isEnabled)
                .isControllerEnabled(isControllerEnabled)
                .rateLimiter(rateLimiter)
                .createdTime(createdTime)
                .lastUpdateTime(lastUpdateTime)
                .lastRegisteredTime(lastRegisteredTime == null ? lastUploadedTime : lastRegisteredTime)
                .lastDeployedTime(lastDeployedTime == null ? lastLoadedTime : lastDeployedTime)
                .lastUndeployedTime(lastUndeployedTime == null ? lastUnloadedTime : lastUndeployedTime)
                .modelId(modelId)
                .autoRedeployRetryTimes(autoRedeployRetryTimes)
                .chunkNumber(chunkNumber)
                .totalChunks(totalChunks)
                .planningWorkerNodeCount(planningWorkerNodeCount)
                .currentWorkerNodeCount(currentWorkerNodeCount)
                .planningWorkerNodes(planningWorkerNodes.toArray(new String[0]))
                .deployToAllNodes(deployToAllNodes)
                .isHidden(isHidden)
                .connector(connector)
                .connectorId(connectorId)
                .guardrails(guardrails)
                .modelInterface(modelInterface)
                .tenantId(tenantId)
                .build();
    }

    public static MLModel fromStream(StreamInput in) throws IOException {
        MLModel mlModel = new MLModel(in);
        return mlModel;
    }

}
