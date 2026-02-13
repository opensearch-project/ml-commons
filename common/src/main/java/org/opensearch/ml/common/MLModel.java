/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;
import static org.opensearch.ml.common.CommonValue.TENANT_ID_FIELD;
import static org.opensearch.ml.common.CommonValue.USER;
import static org.opensearch.ml.common.CommonValue.VERSION_2_19_0;
import static org.opensearch.ml.common.connector.Connector.createConnector;
import static org.opensearch.ml.common.utils.StringUtils.filteredParameterMap;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import org.json.JSONObject;
import org.opensearch.Version;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.connector.Connector;
import org.opensearch.ml.common.connector.ConnectorAction;
import org.opensearch.ml.common.controller.MLRateLimiter;
import org.opensearch.ml.common.model.BaseModelConfig;
import org.opensearch.ml.common.model.Guardrails;
import org.opensearch.ml.common.model.MLDeploySetting;
import org.opensearch.ml.common.model.MLModelConfig;
import org.opensearch.ml.common.model.MLModelFormat;
import org.opensearch.ml.common.model.MLModelState;
import org.opensearch.ml.common.model.MetricsCorrelationModelConfig;
import org.opensearch.ml.common.model.QuestionAnsweringModelConfig;
import org.opensearch.ml.common.model.RemoteModelConfig;
import org.opensearch.ml.common.model.TextEmbeddingModelConfig;
import org.opensearch.telemetry.metrics.tags.Tags;

import com.google.common.annotations.VisibleForTesting;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;

@Getter
@Log4j2
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

    private static final String TAG_DEPLOYMENT = "deployment";
    private static final String TAG_REMOTE_DEPLOYMENT_VALUE = "remote";
    private static final String TAG_PRE_TRAINED_DEPLOYMENT_VALUE = "local:pre_trained";
    private static final String TAG_CUSTOM_DEPLOYMENT_VALUE = "local:custom";
    private static final String TAG_ALGORITHM = "algorithm";
    private static final String TAG_MODEL = "model";
    private static final String TAG_SERVICE_PROVIDER = "service_provider";
    private static final String TAG_VALUE_UNKNOWN = "unknown";
    private static final String TAG_TYPE = "type";
    private static final String TAG_MODEL_FORMAT = "model_format";
    private static final String TAG_URL = "url";

    // do not modify -- used to match keywords in endpoints
    private static final String BEDROCK = "bedrock";
    private static final String SAGEMAKER = "sagemaker";
    private static final String AZURE = "azure";
    private static final String GOOGLE = "google";
    private static final String OPENAI = "openai";
    private static final String DEEPSEEK = "deepseek";
    private static final String COHERE = "cohere";
    private static final String VERTEXAI = "vertexai";
    private static final String ALEPH_ALPHA = "aleph-alpha";
    private static final String COMPREHEND = "comprehend";
    private static final String TEXTRACT = "textract";
    private static final String ANTHROPIC = "anthropic";
    private static final String MISTRAL = "mistral";
    private static final String X_AI = "x.ai";

    // Maintain order (generic providers -> specific providers)
    private static final List<String> MODEL_SERVICE_PROVIDER_KEYWORDS = Arrays
        .asList(
            BEDROCK,
            SAGEMAKER,
            AZURE,
            GOOGLE,
            ANTHROPIC,
            OPENAI,
            DEEPSEEK,
            COHERE,
            VERTEXAI,
            ALEPH_ALPHA,
            COMPREHEND,
            TEXTRACT,
            MISTRAL,
            X_AI
        );

    private static final String LLM_MODEL_TYPE = "llm";
    private static final String EMBEDDING_MODEL_TYPE = "embedding";
    private static final String IMAGE_GENERATION_MODEL_TYPE = "image_generation";
    private static final String SPEECH_AUDIO_MODEL_TYPE = "speech_audio";

    // keywords in model name used to infer type of remote model
    private static final List<String> LLM_KEYWORDS = Arrays
        .asList(
            "gpt",
            "o3",
            "o4-mini",
            "claude",
            "llama",
            "mistral",
            "mixtral",
            "gemini",
            "palm",
            "bard",
            "j1-",
            "j2-",
            "jurassic",
            "command",
            "grok",
            "chat",
            "llm"
        );

    private static final List<String> EMBEDDING_KEYWORDS = Arrays.asList("embedding", "embed", "ada", "text-similarity-");

    private static final List<String> IMAGE_GEN_KEYWORDS = Arrays.asList("diffusion", "dall-e", "imagen", "midjourney", "image");

    private static final List<String> SPEECH_AUDIO_KEYWORDS = Arrays.asList("whisper", "audio", "speech");

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
    public MLModel(
        String name,
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
        String modelId,
        Integer chunkNumber,
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
        String tenantId
    ) {
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
        Version streamInputVersion = input.getVersion();
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
                } else if (algorithm.equals(FunctionName.TEXT_EMBEDDING)) {
                    modelConfig = new TextEmbeddingModelConfig(input);
                } else if (algorithm.equals(FunctionName.REMOTE)) {
                    modelConfig = new RemoteModelConfig(input);
                } else {
                    modelConfig = new BaseModelConfig(input);
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
            this.tenantId = streamInputVersion.onOrAfter(VERSION_2_19_0) ? input.readOptionalString() : null;
        }
    }

    public void writeTo(StreamOutput out) throws IOException {
        Version streamOutputVersion = out.getVersion();
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
        if (streamOutputVersion.onOrAfter(VERSION_2_19_0)) {
            out.writeOptionalString(tenantId);
        }
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
            builder.field(TENANT_ID_FIELD, tenantId);
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
                    } else if (FunctionName.TEXT_EMBEDDING.name().equals(algorithmName)) {
                        modelConfig = TextEmbeddingModelConfig.parse(parser);
                    } else if (FunctionName.REMOTE.name().equals(algorithmName)) {
                        modelConfig = RemoteModelConfig.parse(parser);
                    } else {
                        modelConfig = BaseModelConfig.parse(parser);
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
                case TENANT_ID_FIELD:
                    tenantId = parser.textOrNull();
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return MLModel
            .builder()
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
        return new MLModel(in);
    }

    public Tags getTags() {
        return getTags(this.connector);
    }

    /**
     * Retrieves the appropriate tags for the ML model based on its type and configuration.
     * The method determines the model type and returns corresponding tags:
     * - For remote models (when algorithm is REMOTE and connector is provided), returns remote model tags
     * - For pre-trained models (identified by name starting with "amazon/" or "huggingface/"), returns pre-trained model tags
     * - For all other cases, returns custom model tags
     *
     * @param connector The connector associated with the model, used to identify remote models
     * @return Tags object containing the appropriate tags for the model type
     */
    public Tags getTags(Connector connector) {
        // if connector is present, model is a remote model
        if (this.algorithm == FunctionName.REMOTE && connector != null) {
            return getRemoteModelTags(connector).addTag(IS_HIDDEN_FIELD, isHidden != null && isHidden);
        }

        // pre-trained models follow a specific naming convention, relying on that to identify a pre-trained model
        if (this.name != null
            && (this.name.startsWith("amazon/") || this.name.startsWith("huggingface/"))
            && this.name.split("/").length >= 3) {
            return getPreTrainedModelTags().addTag(IS_HIDDEN_FIELD, isHidden != null && isHidden);
        }

        return getCustomModelTags().addTag(IS_HIDDEN_FIELD, isHidden != null && isHidden);
    }

    /**
     * Generates tags for a remote ML model based on its connector configuration.
     * This method analyzes the connector's predict action URL and request body to identify:
     * - The service provider (e.g., bedrock, sagemaker, azure, etc.)
     * - The specific model being used
     * - The model type (e.g., llm, embedding, image_generation, speech_audio)
     *
     * The method attempts to extract this information in the following order:
     * 1. From the predict action URL (for service provider and some model identifiers)
     * 2. From the request body JSON (for model name)
     * 3. From the connector parameters (as a fallback for model name)
     *
     * If any information cannot be determined, it will be marked as "unknown" in the tags.
     *
     * @param connector The connector associated with the remote model, containing the predict action configuration
     * @return Tags object containing deployment type, service provider, algorithm, model name, and model type
     * @throws RuntimeException if there are issues parsing the connector configuration
     */
    @VisibleForTesting
    Tags getRemoteModelTags(Connector connector) {
        String serviceProvider = TAG_VALUE_UNKNOWN;
        String model = TAG_VALUE_UNKNOWN;
        String modelType = TAG_VALUE_UNKNOWN;
        String url = TAG_VALUE_UNKNOWN;

        Optional<ConnectorAction> predictAction = connector.findAction(ConnectorAction.ActionType.PREDICT.name());
        if (predictAction.isPresent()) {
            try {
                StringSubstitutor stringSubstitutor = new StringSubstitutor(connector.getParameters(), "${parameters.", "}");
                url = stringSubstitutor.replace(predictAction.get().getUrl()).toLowerCase();

                JSONObject requestBody = null;
                if (predictAction.get().getRequestBody() != null) {
                    try {
                        String body = stringSubstitutor.replace(predictAction.get().getRequestBody());
                        requestBody = new JSONObject(body);
                    } catch (Exception e) {
                        log.error("Failed to parse request body as JSON: {}", e.getMessage());
                    }
                }

                serviceProvider = identifyServiceProvider(url);
                model = identifyModel(serviceProvider, url, requestBody, connector);
                modelType = identifyModelType(model);
            } catch (Exception e) {
                log.warn("Error identifying model provider and model from connector: {}", e.getMessage());
            }
        }

        Tags tags = Tags
            .create()
            .addTag(TAG_DEPLOYMENT, TAG_REMOTE_DEPLOYMENT_VALUE)
            .addTag(TAG_SERVICE_PROVIDER, serviceProvider)
            .addTag(TAG_ALGORITHM, algorithm.name())
            .addTag(TAG_MODEL, model)
            .addTag(TAG_TYPE, modelType);

        if ((serviceProvider.equals(TAG_VALUE_UNKNOWN) || model.equals(TAG_VALUE_UNKNOWN)) && !url.equals(TAG_VALUE_UNKNOWN)) {
            tags.addTag(TAG_URL, url);
        }

        return tags;
    }

    @VisibleForTesting
    /**
     * Identifies the service provider from a URL by checking against known provider keywords.
     * The method checks the URL for the presence of provider keywords in the following order:
     * - bedrock
     * - sagemaker
     * - azure
     * - google
     * - anthropic
     * - openai
     * - deepseek
     * - cohere
     * - vertexai
     * - aleph-alpha
     * - comprehend
     * - textract
     * - mistral
     * - x.ai
     *
     * If no matching provider keyword is found in the URL,
     * returns "unknown" as the service provider.
     *
     * @param url The URL to analyze for service provider identification
     * @return The identified service provider name, or "unknown" if not found
     */
    static String identifyServiceProvider(String url) {
        for (String provider : MODEL_SERVICE_PROVIDER_KEYWORDS) {
            if (url.contains(provider)) {
                return provider;
            }
        }

        return TAG_VALUE_UNKNOWN;
    }

    public static String identifyServiceProviderFromUrl(String url) {
        return identifyServiceProvider(url);
    }

    /**
     * Identifies the model name from the connector configuration using multiple strategies.
     * The method attempts to extract the model name in the following order:
     * 1. For Bedrock models: Extracts model name from the URL path after '/model/'
     * 2. From request body JSON: Checks for 'model' or 'ModelName' fields
     * 3. From connector parameters: Uses the 'model' parameter if available
     *
     * If the model name cannot be determined through any of these methods,
     * returns "unknown".
     *
     * @param provider The service provider (e.g., bedrock, sagemaker, azure)
     * @param url The predict action URL from the connector
     * @param requestBody The JSON request body from the predict action
     * @param connector The connector containing the model configuration
     * @return The identified model name, or "unknown" if not found
     */
    @VisibleForTesting
    String identifyModel(String provider, String url, JSONObject requestBody, Connector connector) {
        try {
            // bedrock expects model in the url after `/model/`
            if (provider.equals(BEDROCK)) {
                Pattern bedrockPattern = Pattern.compile("/model/([^/]+)/");
                Matcher bedrockMatcher = bedrockPattern.matcher(url);
                if (bedrockMatcher.find()) {
                    return bedrockMatcher.group(1);
                }
            }
        } catch (Exception e) {
            log.error("Error extracting model information: {}", e.getMessage());
        }

        // check if request body has `model` -- typical for OpenAI/Sagemaker
        if (requestBody != null) {
            if (requestBody.keySet().contains("model")) {
                return requestBody.getString("model");
            }

            if (requestBody.keySet().contains("ModelName")) {
                return requestBody.getString("ModelName");
            }
        }

        // check if parameters has `model` -- recommended via blueprints
        if (connector.getParameters() != null && connector.getParameters().containsKey("model")) {
            return connector.getParameters().get("model");
        }

        return TAG_VALUE_UNKNOWN;
    }

    private static boolean containsAny(String target, List<String> keywords) {
        for (String key : keywords) {
            if (target.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Identifies the type of model based on keywords in the model name.
     * The method checks for specific keywords in the model name to determine its type:
     * - LLM (Large Language Model): checks for keywords like "gpt", "claude", "llama", etc.
     * - Embedding: checks for keywords like "embedding", "embed", "ada", etc.
     * - Image Generation: checks for keywords like "diffusion", "dall-e", "imagen", etc.
     * - Speech/Audio: checks for keywords like "whisper", "audio", "speech", etc.
     *
     * If no matching keywords are found or if the model name is null/unknown,
     * returns "unknown" as the model type.
     *
     * @param model The name of the model to identify
     * @return The identified model type (llm, embedding, image_generation, speech_audio, or unknown)
     */
    @VisibleForTesting
    String identifyModelType(String model) {
        if (model == null || TAG_VALUE_UNKNOWN.equals(model)) {
            return TAG_VALUE_UNKNOWN;
        }

        String modelLower = model.toLowerCase();

        if (containsAny(modelLower, LLM_KEYWORDS)) {
            return LLM_MODEL_TYPE;
        }

        if (containsAny(modelLower, EMBEDDING_KEYWORDS)) {
            return EMBEDDING_MODEL_TYPE;
        }

        if (containsAny(modelLower, IMAGE_GEN_KEYWORDS)) {
            return IMAGE_GENERATION_MODEL_TYPE;
        }

        if (containsAny(modelLower, SPEECH_AUDIO_KEYWORDS)) {
            return SPEECH_AUDIO_MODEL_TYPE;
        }

        return TAG_VALUE_UNKNOWN;
    }

    /**
     * Generates tags for a pre-trained ML model based on its name and configuration.
     * This method is specifically designed for models that follow the naming convention
     * "provider/algorithm/model" (e.g., "amazon/bert/model-name" or "huggingface/bert/model-name").
     *
     * The method extracts the following information:
     * - Service provider from the first part of the model name
     * - Algorithm from the model's algorithm field
     * - Model name from the third part of the model name
     * - Model type from the model configuration (if available)
     * - Model format from the model's format field (if available)
     *
     * @return Tags object containing deployment type (pre-trained), service provider,
     *         algorithm, model name, model type, and model format (if available)
     */
    @VisibleForTesting
    Tags getPreTrainedModelTags() {
        String modelType = TAG_VALUE_UNKNOWN;
        if (this.modelConfig != null && this.modelConfig.getModelType() != null) {
            modelType = this.modelConfig.getModelType();
        }

        String[] nameParts = this.name.split("/");
        Tags tags = Tags
            .create()
            .addTag(TAG_DEPLOYMENT, TAG_PRE_TRAINED_DEPLOYMENT_VALUE)
            .addTag(TAG_SERVICE_PROVIDER, nameParts[0])
            .addTag(TAG_ALGORITHM, this.algorithm.name()) // nameParts[1] is not used
            .addTag(TAG_MODEL, nameParts[2])
            .addTag(TAG_TYPE, modelType);

        if (this.modelFormat != null) {
            tags.addTag(TAG_MODEL_FORMAT, this.modelFormat.name());
        }

        return tags;
    }

    /**
     * Generates tags for a custom ML model based on its configuration.
     * This method is used for models that do not follow the pre-trained naming convention
     * (e.g., "model-name" or "model-name/model-name").
     *
     * The method extracts the following information:
     * - Model type from the model configuration (if available)
     * - Model format from the model's format field (if available)
     *
     * @return Tags object containing deployment type (custom), algorithm, and model type (if available)
     */
    @VisibleForTesting
    Tags getCustomModelTags() {
        String modelType = TAG_VALUE_UNKNOWN;
        if (this.modelConfig != null && this.modelConfig.getModelType() != null) {
            modelType = this.modelConfig.getModelType();
        }

        Tags tags = Tags
            .create()
            .addTag(TAG_DEPLOYMENT, TAG_CUSTOM_DEPLOYMENT_VALUE)
            .addTag(TAG_ALGORITHM, this.algorithm.name())
            .addTag(TAG_TYPE, modelType);

        if (this.modelFormat != null) {
            tags.addTag(TAG_MODEL_FORMAT, this.modelFormat.name());
        }

        return tags;
    }
}
