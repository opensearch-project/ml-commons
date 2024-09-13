/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import static org.opensearch.ml.common.MLConfig.CONFIG_TYPE_FIELD;
import static org.opensearch.ml.common.MLConfig.LAST_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.MLConfig.ML_CONFIGURATION_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.APPLICATION_TYPE_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ADDITIONAL_INFO_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_CONVERSATION_ID_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_CREATE_TIME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INDEX_SCHEMA_VERSION;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_INPUT_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_ORIGIN_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_PROMPT_TEMPLATE_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_RESPONSE_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.INTERACTIONS_TRACE_NUMBER_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_CREATED_TIME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_INDEX_SCHEMA_VERSION;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_NAME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.META_UPDATED_TIME_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.PARENT_INTERACTIONS_ID_FIELD;
import static org.opensearch.ml.common.conversation.ConversationalIndexConstants.USER_FIELD;
import static org.opensearch.ml.common.model.MLModelConfig.ALL_CONFIG_FIELD;
import static org.opensearch.ml.common.model.MLModelConfig.MODEL_TYPE_FIELD;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.EMBEDDING_DIMENSION_FIELD;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.FRAMEWORK_TYPE_FIELD;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.MODEL_MAX_LENGTH_FIELD;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.NORMALIZE_RESULT_FIELD;
import static org.opensearch.ml.common.model.TextEmbeddingModelConfig.POOLING_MODE_FIELD;

import java.util.Set;

import org.opensearch.Version;
import org.opensearch.ml.common.agent.MLAgent;
import org.opensearch.ml.common.connector.AbstractConnector;
import org.opensearch.ml.common.controller.MLController;

import com.google.common.collect.ImmutableSet;

public class CommonValue {

    public static Integer NO_SCHEMA_VERSION = 0;
    public static final String REMOTE_SERVICE_ERROR = "Error from remote service: ";
    public static final String USER = "user";
    public static final String META = "_meta";
    public static final String SCHEMA_VERSION_FIELD = "schema_version";
    public static final String UNDEPLOYED = "undeployed";
    public static final String NOT_FOUND = "not_found";

    public static final String MASTER_KEY = "master_key";
    public static final String CREATE_TIME_FIELD = "create_time";
    public static final String LAST_UPDATE_TIME_FIELD = "last_update_time";

    public static final String BOX_TYPE_KEY = "box_type";
    // hot node
    public static String HOT_BOX_TYPE = "hot";
    // warm node
    public static String WARM_BOX_TYPE = "warm";
    public static final String ML_MODEL_GROUP_INDEX = ".plugins-ml-model-group";
    public static final String ML_MODEL_INDEX = ".plugins-ml-model";
    public static final String ML_TASK_INDEX = ".plugins-ml-task";
    public static final Integer ML_MODEL_GROUP_INDEX_SCHEMA_VERSION = 2;
    public static final Integer ML_MODEL_INDEX_SCHEMA_VERSION = 11;
    public static final String ML_CONNECTOR_INDEX = ".plugins-ml-connector";
    public static final Integer ML_TASK_INDEX_SCHEMA_VERSION = 3;
    public static final Integer ML_CONNECTOR_SCHEMA_VERSION = 3;
    public static final String ML_CONFIG_INDEX = ".plugins-ml-config";
    public static final Integer ML_CONFIG_INDEX_SCHEMA_VERSION = 3;
    public static final String ML_CONTROLLER_INDEX = ".plugins-ml-controller";
    public static final Integer ML_CONTROLLER_INDEX_SCHEMA_VERSION = 1;
    public static final String ML_MAP_RESPONSE_KEY = "response";
    public static final String ML_AGENT_INDEX = ".plugins-ml-agent";
    public static final Integer ML_AGENT_INDEX_SCHEMA_VERSION = 2;
    public static final String ML_MEMORY_META_INDEX = ".plugins-ml-memory-meta";
    public static final Integer ML_MEMORY_META_INDEX_SCHEMA_VERSION = 1;
    public static final String ML_MEMORY_MESSAGE_INDEX = ".plugins-ml-memory-message";
    public static final String ML_STOP_WORDS_INDEX = ".plugins-ml-stop-words";
    public static final Set<String> stopWordsIndices = ImmutableSet.of(".plugins-ml-stop-words");
    public static final Integer ML_MEMORY_MESSAGE_INDEX_SCHEMA_VERSION = 1;
    public static final String USER_FIELD_MAPPING = "      \""
        + CommonValue.USER
        + "\": {\n"
        + "        \"type\": \"nested\",\n"
        + "        \"properties\": {\n"
        + "          \"name\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\", \"ignore_above\":256}}},\n"
        + "          \"backend_roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"custom_attribute_names\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}}\n"
        + "        }\n"
        + "      }\n";
    public static final String ML_MODEL_GROUP_INDEX_MAPPING = "{\n"
        + "  \"_meta\": {\n"
        + "    \"schema_version\": "
        + ML_MODEL_GROUP_INDEX_SCHEMA_VERSION
        + "\n"
        + "  },\n"
        + "  \"properties\": {\n"
        + "    \""
        + MLModelGroup.MODEL_GROUP_NAME_FIELD
        + "\": {\n"
        + "      \"type\": \"text\",\n"
        + "      \"fields\": {\n"
        + "        \"keyword\": {\n"
        + "          \"type\": \"keyword\",\n"
        + "          \"ignore_above\": 256\n"
        + "        }\n"
        + "      }\n"
        + "    },\n"
        + "    \""
        + MLModelGroup.DESCRIPTION_FIELD
        + "\": {\n"
        + "      \"type\": \"text\"\n"
        + "    },\n"
        + "    \""
        + MLModelGroup.LATEST_VERSION_FIELD
        + "\": {\n"
        + "      \"type\": \"integer\"\n"
        + "    },\n"
        + "   \""
        + MLModelGroup.MODEL_GROUP_ID_FIELD
        + "\": {\n"
        + "      \"type\": \"keyword\"\n"
        + "    },\n"
        + "    \""
        + MLModelGroup.BACKEND_ROLES_FIELD
        + "\": {\n"
        + "      \"type\": \"text\",\n"
        + "      \"fields\": {\n"
        + "        \"keyword\": {\n"
        + "          \"type\": \"keyword\",\n"
        + "          \"ignore_above\": 256\n"
        + "        }\n"
        + "      }\n"
        + "    },\n"
        + "   \""
        + MLModelGroup.ACCESS
        + "\": {\n"
        + "      \"type\": \"keyword\"\n"
        + "    },\n"
        + "    \""
        + MLModelGroup.OWNER
        + "\": {\n"
        + "      \"type\": \"nested\",\n"
        + "        \"properties\": {\n"
        + "          \"name\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\", \"ignore_above\":256}}},\n"
        + "          \"backend_roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"custom_attribute_names\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}}\n"
        + "        }\n"
        + "    },\n"
        + "     \""
        + MLModelGroup.CREATED_TIME_FIELD
        + "\": {\n"
        + "      \"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "    \""
        + MLModelGroup.LAST_UPDATED_TIME_FIELD
        + "\": {\n"
        + "      \"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"}\n"
        + "  }\n"
        + "}";

    public static final String ML_CONNECTOR_INDEX_FIELDS = "    \"properties\": {\n"
        + "      \""
        + AbstractConnector.NAME_FIELD
        + "\" : {\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\n"
        + "      \""
        + AbstractConnector.VERSION_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + AbstractConnector.DESCRIPTION_FIELD
        + "\" : {\"type\": \"text\"},\n"
        + "      \""
        + AbstractConnector.PROTOCOL_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + AbstractConnector.PARAMETERS_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + AbstractConnector.CREDENTIAL_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + AbstractConnector.CLIENT_CONFIG_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + AbstractConnector.ACTIONS_FIELD
        + "\" : {\"type\": \"flat_object\"}\n";

    public static final String ML_MODEL_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_MODEL_INDEX_SCHEMA_VERSION
        + "},\n"
        + "    \"properties\": {\n"
        + "      \""
        + MLModel.ALGORITHM_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.MODEL_NAME_FIELD
        + "\" : {\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\n"
        + "      \""
        + MLModel.OLD_MODEL_VERSION_FIELD
        + "\" : {\"type\": \"long\"},\n"
        + "      \""
        + MLModel.MODEL_VERSION_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.MODEL_GROUP_ID_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.MODEL_CONTENT_FIELD
        + "\" : {\"type\": \"binary\"},\n"
        + "      \""
        + MLModel.CHUNK_NUMBER_FIELD
        + "\" : {\"type\": \"long\"},\n"
        + "      \""
        + MLModel.TOTAL_CHUNKS_FIELD
        + "\" : {\"type\": \"long\"},\n"
        + "      \""
        + MLModel.MODEL_ID_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.DESCRIPTION_FIELD
        + "\" : {\"type\": \"text\"},\n"
        + "      \""
        + MLModel.MODEL_FORMAT_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.MODEL_STATE_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.MODEL_CONTENT_SIZE_IN_BYTES_FIELD
        + "\" : {\"type\": \"long\"},\n"
        + "      \""
        + MLModel.PLANNING_WORKER_NODE_COUNT_FIELD
        + "\" : {\"type\": \"integer\"},\n"
        + "      \""
        + MLModel.CURRENT_WORKER_NODE_COUNT_FIELD
        + "\" : {\"type\": \"integer\"},\n"
        + "      \""
        + MLModel.PLANNING_WORKER_NODES_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.DEPLOY_TO_ALL_NODES_FIELD
        + "\": {\"type\": \"boolean\"},\n"
        + "      \""
        + MLModel.IS_HIDDEN_FIELD
        + "\": {\"type\": \"boolean\"},\n"
        + "      \""
        + MLModel.MODEL_CONFIG_FIELD
        + "\" : {\"properties\":{\""
        + MODEL_TYPE_FIELD
        + "\":{\"type\":\"keyword\"},\""
        + EMBEDDING_DIMENSION_FIELD
        + "\":{\"type\":\"integer\"},\""
        + FRAMEWORK_TYPE_FIELD
        + "\":{\"type\":\"keyword\"},\""
        + POOLING_MODE_FIELD
        + "\":{\"type\":\"keyword\"},\""
        + NORMALIZE_RESULT_FIELD
        + "\":{\"type\":\"boolean\"},\""
        + MODEL_MAX_LENGTH_FIELD
        + "\":{\"type\":\"integer\"},\""
        + ALL_CONFIG_FIELD
        + "\":{\"type\":\"text\"}}},\n"
        + "      \""
        + MLModel.DEPLOY_SETTING_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLModel.IS_ENABLED_FIELD
        + "\" : {\"type\": \"boolean\"},\n"
        + "      \""
        + MLModel.IS_CONTROLLER_ENABLED_FIELD
        + "\" : {\"type\": \"boolean\"},\n"
        + "      \""
        + MLModel.RATE_LIMITER_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLModel.MODEL_CONTENT_HASH_VALUE_FIELD
        + "\" : {\"type\": \"keyword\"},\n"
        + "      \""
        + MLModel.AUTO_REDEPLOY_RETRY_TIMES_FIELD
        + "\" : {\"type\": \"integer\"},\n"
        + "      \""
        + MLModel.CREATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLModel.LAST_UPDATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLModel.LAST_REGISTERED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLModel.LAST_DEPLOYED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLModel.LAST_UNDEPLOYED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLModel.INTERFACE_FIELD
        + "\": {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLModel.GUARDRAILS_FIELD
        + "\" : {\n"
        + "          \"properties\": {\n"
        + "            \"input_guardrail\": {\n"
        + "              \"properties\": {\n"
        + "                \"regex\": {\n"
        + "                  \"type\": \"text\"\n"
        + "                },\n"
        + "                \"stop_words\": {\n"
        + "                  \"properties\": {\n"
        + "                    \"index_name\": {\n"
        + "                      \"type\": \"text\"\n"
        + "                    },\n"
        + "                    \"source_fields\": {\n"
        + "                      \"type\": \"text\"\n"
        + "                    }\n"
        + "                  }\n"
        + "                }\n"
        + "              }\n"
        + "            },\n"
        + "            \"output_guardrail\": {\n"
        + "              \"properties\": {\n"
        + "                \"regex\": {\n"
        + "                  \"type\": \"text\"\n"
        + "                },\n"
        + "                \"stop_words\": {\n"
        + "                  \"properties\": {\n"
        + "                    \"index_name\": {\n"
        + "                      \"type\": \"text\"\n"
        + "                    },\n"
        + "                    \"source_fields\": {\n"
        + "                      \"type\": \"text\"\n"
        + "                    }\n"
        + "                  }\n"
        + "                }\n"
        + "              }\n"
        + "            }\n"
        + "          }\n"
        + "        },\n"
        + "      \""
        + MLModel.CONNECTOR_FIELD
        + "\": {"
        + ML_CONNECTOR_INDEX_FIELDS
        + "    }\n},"
        + USER_FIELD_MAPPING
        + "    }\n"
        + "}";

    public static final String ML_TASK_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_TASK_INDEX_SCHEMA_VERSION
        + "},\n"
        + "    \"properties\": {\n"
        + "      \""
        + MLTask.MODEL_ID_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.TASK_TYPE_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.FUNCTION_NAME_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.STATE_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.INPUT_TYPE_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.PROGRESS_FIELD
        + "\": {\"type\": \"float\"},\n"
        + "      \""
        + MLTask.OUTPUT_INDEX_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.WORKER_NODE_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + MLTask.CREATE_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLTask.LAST_UPDATE_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLTask.ERROR_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "      \""
        + MLTask.IS_ASYNC_TASK_FIELD
        + "\" : {\"type\" : \"boolean\"}, \n"
        + "      \""
        + MLTask.REMOTE_JOB_FIELD
        + "\" : {\"type\": \"flat_object\"}, \n"
        + USER_FIELD_MAPPING
        + "    }\n"
        + "}";

    public static final String ML_CONNECTOR_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_CONNECTOR_SCHEMA_VERSION
        + "},\n"
        + ML_CONNECTOR_INDEX_FIELDS
        + ",\n"
        + "      \""
        + MLModelGroup.BACKEND_ROLES_FIELD
        + "\": {\n"
        + "   \"type\": \"text\",\n"
        + "      \"fields\": {\n"
        + "        \"keyword\": {\n"
        + "          \"type\": \"keyword\",\n"
        + "          \"ignore_above\": 256\n"
        + "        }\n"
        + "      }\n"
        + "    },\n"
        + "   \""
        + MLModelGroup.ACCESS
        + "\": {\n"
        + "   \"type\": \"keyword\"\n"
        + "    },\n"
        + "  \""
        + MLModelGroup.OWNER
        + "\": {\n"
        + "    \"type\": \"nested\",\n"
        + "        \"properties\": {\n"
        + "          \"name\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\", \"ignore_above\":256}}},\n"
        + "          \"backend_roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"roles\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}},\n"
        + "          \"custom_attribute_names\": {\"type\":\"text\", \"fields\":{\"keyword\":{\"type\":\"keyword\"}}}\n"
        + "        }\n"
        + "    },\n"
        + "  \""
        + AbstractConnector.CREATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + AbstractConnector.LAST_UPDATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"}\n"
        + "    }\n"
        + "}";

    public static final String ML_CONFIG_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_CONFIG_INDEX_SCHEMA_VERSION
        + "},\n"
        + "    \"properties\": {\n"
        + "      \""
        + MASTER_KEY
        + "\": {\"type\": \"keyword\"},\n"
        + "      \""
        + CONFIG_TYPE_FIELD
        + "\" : {\"type\":\"keyword\"},\n"
        + "      \""
        + ML_CONFIGURATION_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + CREATE_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + LAST_UPDATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"}\n"
        + "    }\n"
        + "}";

    public static final String ML_CONTROLLER_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_CONTROLLER_INDEX_SCHEMA_VERSION
        + "},\n"
        + "    \"properties\": {\n"
        + "      \""
        + MLController.USER_RATE_LIMITER
        + "\" : {\"type\": \"flat_object\"}\n"
        + "    }\n"
        + "}";

    public static final String ML_AGENT_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\"schema_version\": "
        + ML_AGENT_INDEX_SCHEMA_VERSION
        + "},\n"
        + "    \"properties\": {\n"
        + "      \""
        + MLAgent.AGENT_NAME_FIELD
        + "\" : {\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\",\"ignore_above\":256}}},\n"
        + "      \""
        + MLAgent.AGENT_TYPE_FIELD
        + "\" : {\"type\":\"keyword\"},\n"
        + "      \""
        + MLAgent.DESCRIPTION_FIELD
        + "\" : {\"type\": \"text\"},\n"
        + "      \""
        + MLAgent.LLM_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLAgent.TOOLS_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLAgent.PARAMETERS_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLAgent.MEMORY_FIELD
        + "\" : {\"type\": \"flat_object\"},\n"
        + "      \""
        + MLAgent.IS_HIDDEN_FIELD
        + "\": {\"type\": \"boolean\"},\n"
        + "      \""
        + MLAgent.CREATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "      \""
        + MLAgent.LAST_UPDATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"}\n"
        + "    }\n"
        + "}";

    public static final String ML_MEMORY_META_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\n"
        + "        \"schema_version\": "
        + META_INDEX_SCHEMA_VERSION
        + "\n"
        + "    },\n"
        + "    \"properties\": {\n"
        + "        \""
        + META_NAME_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + META_CREATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "        \""
        + META_UPDATED_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "        \""
        + USER_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + APPLICATION_TYPE_FIELD
        + "\": {\"type\": \"keyword\"}\n"
        + "    }\n"
        + "}";

    public static final String ML_MEMORY_MESSAGE_INDEX_MAPPING = "{\n"
        + "    \"_meta\": {\n"
        + "        \"schema_version\": "
        + INTERACTIONS_INDEX_SCHEMA_VERSION
        + "\n"
        + "    },\n"
        + "    \"properties\": {\n"
        + "        \""
        + INTERACTIONS_CONVERSATION_ID_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + INTERACTIONS_CREATE_TIME_FIELD
        + "\": {\"type\": \"date\", \"format\": \"strict_date_time||epoch_millis\"},\n"
        + "        \""
        + INTERACTIONS_INPUT_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + INTERACTIONS_PROMPT_TEMPLATE_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + INTERACTIONS_RESPONSE_FIELD
        + "\": {\"type\": \"text\"},\n"
        + "        \""
        + INTERACTIONS_ORIGIN_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + INTERACTIONS_ADDITIONAL_INFO_FIELD
        + "\": {\"type\": \"flat_object\"},\n"
        + "        \""
        + PARENT_INTERACTIONS_ID_FIELD
        + "\": {\"type\": \"keyword\"},\n"
        + "        \""
        + INTERACTIONS_TRACE_NUMBER_FIELD
        + "\": {\"type\": \"long\"}\n"
        + "    }\n"
        + "}";
    // Calculate Versions independently of OpenSearch core version
    public static final Version VERSION_2_11_0 = Version.fromString("2.11.0");
    public static final Version VERSION_2_12_0 = Version.fromString("2.12.0");
    public static final Version VERSION_2_13_0 = Version.fromString("2.13.0");
    public static final Version VERSION_2_14_0 = Version.fromString("2.14.0");
    public static final Version VERSION_2_15_0 = Version.fromString("2.15.0");
    public static final Version VERSION_2_16_0 = Version.fromString("2.16.0");
    public static final Version VERSION_2_17_0 = Version.fromString("2.17.0");
}
