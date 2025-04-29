/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Set;

import org.opensearch.Version;
import org.opensearch.common.settings.Setting;

import com.google.common.collect.ImmutableSet;

public class CommonValue {

    public static Integer NO_SCHEMA_VERSION = 0;
    public static final String REMOTE_SERVICE_ERROR = "Error from remote service: ";
    public static final String USER = "user";
    public static final String META = "_meta";
    public static final String SCHEMA_VERSION_FIELD = "schema_version";
    public static final String UNDEPLOYED = "undeployed";
    public static final String NOT_FOUND = "not_found";

    /** The field name containing the tenant id */
    public static final String TENANT_ID_FIELD = "tenant_id";

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
    public static final String ML_CONNECTOR_INDEX = ".plugins-ml-connector";
    public static final String ML_CONFIG_INDEX = ".plugins-ml-config";
    public static final String ML_CONTROLLER_INDEX = ".plugins-ml-controller";
    public static final String ML_MAP_RESPONSE_KEY = "response";
    public static final String ML_AGENT_INDEX = ".plugins-ml-agent";
    public static final String ML_MEMORY_META_INDEX = ".plugins-ml-memory-meta";
    public static final String ML_MEMORY_MESSAGE_INDEX = ".plugins-ml-memory-message";
    public static final String ML_STOP_WORDS_INDEX = ".plugins-ml-stop-words";
    public static final String TASK_POLLING_JOB_INDEX = ".ml_commons_task_polling_job";
    public static final String MCP_SESSION_MANAGEMENT_INDEX = ".plugins-ml-mcp-session-management";
    public static final Set<String> stopWordsIndices = ImmutableSet.of(".plugins-ml-stop-words");
    public static final String TOOL_PARAMETERS_PREFIX = "tools.parameters.";

    // Index mapping paths
    public static final String ML_MODEL_GROUP_INDEX_MAPPING_PATH = "index-mappings/ml_model_group.json";
    public static final String ML_MODEL_INDEX_MAPPING_PATH = "index-mappings/ml_model.json";
    public static final String ML_TASK_INDEX_MAPPING_PATH = "index-mappings/ml_task.json";
    public static final String ML_CONNECTOR_INDEX_MAPPING_PATH = "index-mappings/ml_connector.json";
    public static final String ML_CONFIG_INDEX_MAPPING_PATH = "index-mappings/ml_config.json";
    public static final String ML_CONTROLLER_INDEX_MAPPING_PATH = "index-mappings/ml_controller.json";
    public static final String ML_AGENT_INDEX_MAPPING_PATH = "index-mappings/ml_agent.json";
    public static final String ML_MEMORY_META_INDEX_MAPPING_PATH = "index-mappings/ml_memory_meta.json";
    public static final String ML_MEMORY_MESSAGE_INDEX_MAPPING_PATH = "index-mappings/ml_memory_message.json";
    public static final String ML_MCP_SESSION_MANAGEMENT_INDEX_MAPPING_PATH = "index-mappings/ml_mcp_session_management.json";

    // Calculate Versions independently of OpenSearch core version
    public static final Version VERSION_2_11_0 = Version.fromString("2.11.0");
    public static final Version VERSION_2_12_0 = Version.fromString("2.12.0");
    public static final Version VERSION_2_13_0 = Version.fromString("2.13.0");
    public static final Version VERSION_2_14_0 = Version.fromString("2.14.0");
    public static final Version VERSION_2_15_0 = Version.fromString("2.15.0");
    public static final Version VERSION_2_16_0 = Version.fromString("2.16.0");
    public static final Version VERSION_2_17_0 = Version.fromString("2.17.0");
    public static final Version VERSION_2_18_0 = Version.fromString("2.18.0");
    public static final Version VERSION_2_19_0 = Version.fromString("2.19.0");
    public static final Version VERSION_3_0_0 = Version.fromString("3.0.0");

    // Connector Constants
    public static final String NAME_FIELD = "name";
    public static final String VERSION_FIELD = "version";
    public static final String DESCRIPTION_FIELD = "description";
    public static final String PROTOCOL_FIELD = "protocol";
    public static final String CREDENTIAL_FIELD = "credential";
    public static final String PARAMETERS_FIELD = "parameters";
    public static final String CREATED_TIME_FIELD = "created_time";
    public static final String LAST_UPDATED_TIME_FIELD = "last_updated_time";
    public static final String BACKEND_ROLES_FIELD = "backend_roles";
    public static final String OWNER_FIELD = "owner";
    public static final String ACCESS_FIELD = "access";
    public static final String CLIENT_CONFIG_FIELD = "client_config";
    public static final String URL_FIELD = "url";
    public static final String HEADERS_FIELD = "headers";

    // MCP Constants
    public static final String MCP_TOOL_NAME_FIELD = "name";
    public static final String MCP_TOOL_DESCRIPTION_FIELD = "description";
    public static final String MCP_TOOL_INPUT_SCHEMA_FIELD = "inputSchema";
    public static final String MCP_SYNC_CLIENT = "mcp_sync_client";
    public static final String MCP_TOOLS_FIELD = "tools";
    public static final String MCP_CONNECTORS_FIELD = "mcp_connectors";
    public static final String MCP_CONNECTOR_ID_FIELD = "mcp_connector_id";

    public static final Setting<Boolean> ML_COMMONS_MCP_FEATURE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.mcp_feature_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final String ML_COMMONS_MCP_FEATURE_DISABLED_MESSAGE =
        "The MCP feature is not enabled. To enable, please update the setting " + ML_COMMONS_MCP_FEATURE_ENABLED.getKey();

    // TOOL Constants
    public static final String TOOL_INPUT_SCHEMA_FIELD = "input_schema";
}
