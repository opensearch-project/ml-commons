/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Set;

import org.opensearch.Version;

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

    public static final String AGENTIC_MEMORY_THREAD_POOL = "opensearch_ml_agentic_memory";

    public static final String BOX_TYPE_KEY = "box_type";
    // hot node
    public static String HOT_BOX_TYPE = "hot";
    // warm node
    public static String WARM_BOX_TYPE = "warm";
    public static final String ML_INDEX_INSIGHT_CONFIG_INDEX = ".plugins-ml-index-insight-config";
    public static final String ML_INDEX_INSIGHT_STORAGE_INDEX = ".plugins-ml-index-insight-storage";

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
    public static final String ML_MEMORY_CONTAINER_INDEX = ".plugins-ml-am-memory-container";
    public static final String ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX = ".plugins-ml-am";
    public static final String ML_AGENTIC_MEMORY_INDEX_PATTERN = ML_AGENTIC_MEMORY_SYSTEM_INDEX_PREFIX + "*";
    public static final String ML_STOP_WORDS_INDEX = ".plugins-ml-stop-words";
    // index used in 2.19 to track MlTaskBatchUpdate task
    public static final String TASK_POLLING_JOB_INDEX = ".ml_commons_task_polling_job";
    public static final String MCP_SESSION_MANAGEMENT_INDEX = ".plugins-ml-mcp-session-management";
    public static final String MCP_TOOLS_INDEX = ".plugins-ml-mcp-tools";
    public static final String ML_CONTEXT_MANAGEMENT_TEMPLATES_INDEX = ".plugins-ml-context-management-templates";
    // index created in 3.1 to track all ml jobs created via job scheduler
    public static final String ML_JOBS_INDEX = ".plugins-ml-jobs";
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
    public static final String ML_MEMORY_CONTAINER_INDEX_MAPPING_PATH = "index-mappings/ml_memory_container.json";
    public static final String ML_MEMORY_SESSION_INDEX_MAPPING_PATH = "index-mappings/ml_memory_sessions.json";
    public static final String ML_WORKING_MEMORY_INDEX_MAPPING_PATH = "index-mappings/ml_memory_working.json";
    public static final String ML_LONG_TERM_MEMORY_INDEX_MAPPING_PATH = "index-mappings/ml_memory_long_term.json";
    public static final String ML_LONG_MEMORY_HISTORY_INDEX_MAPPING_PATH = "index-mappings/ml_memory_long_term_history.json";
    public static final String ML_MCP_SESSION_MANAGEMENT_INDEX_MAPPING_PATH = "index-mappings/ml_mcp_session_management.json";
    public static final String ML_MCP_TOOLS_INDEX_MAPPING_PATH = "index-mappings/ml_mcp_tools.json";
    public static final String ML_CONTEXT_MANAGEMENT_TEMPLATES_INDEX_MAPPING_PATH = "index-mappings/ml_context_management_templates.json";
    public static final String ML_JOBS_INDEX_MAPPING_PATH = "index-mappings/ml_jobs.json";
    public static final String ML_INDEX_INSIGHT_CONFIG_INDEX_MAPPING_PATH = "index-mappings/ml_index_insight_config.json";
    public static final String ML_INDEX_INSIGHT_STORAGE_INDEX_MAPPING_PATH = "index-mappings/ml_index_insight_storage.json";

    // Resource type used in resource-access-control
    public static final String ML_MODEL_GROUP_RESOURCE_TYPE = "ml-model-group";

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
    public static final Version VERSION_3_1_0 = Version.fromString("3.1.0");
    public static final Version VERSION_3_2_0 = Version.fromString("3.2.0");
    public static final Version VERSION_3_3_0 = Version.fromString("3.3.0");

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
    public static final String CONNECTOR_ACTION_FIELD = "connector_action";

    // MCP Constants
    public static final String MCP_TOOL_NAME_FIELD = "name";
    public static final String MCP_TOOL_DESCRIPTION_FIELD = "description";
    public static final String MCP_TOOL_INPUT_SCHEMA_FIELD = "inputSchema";
    public static final String MCP_SYNC_CLIENT = "mcp_sync_client";
    public static final String MCP_TOOLS_FIELD = "tools";
    public static final String MCP_CONNECTORS_FIELD = "mcp_connectors";
    public static final String MCP_CONNECTOR_ID_FIELD = "mcp_connector_id";
    public static final String MCP_DEFAULT_SSE_ENDPOINT = "/sse";
    public static final String SSE_ENDPOINT_FIELD = "sse_endpoint";
    public static final String MCP_DEFAULT_STREAMABLE_HTTP_ENDPOINT = "/mcp/";
    public static final String ENDPOINT_FIELD = "endpoint";

    // TOOL Constants
    public static final String TOOL_INPUT_SCHEMA_FIELD = "input_schema";

    public static final String INDEX_INSIGHT_AGENT_NAME = "os_index_insight_agent";
    public static final long INDEX_INSIGHT_GENERATING_TIMEOUT = 3 * 60 * 1000;
    public static final long INDEX_INSIGHT_UPDATE_INTERVAL = 24 * 60 * 60 * 1000;

    // JSON-RPC Error Codes
    public static final int JSON_RPC_PARSE_ERROR = -32700;
    public static final int JSON_RPC_INTERNAL_ERROR = -32603;
    public static final int JSON_RPC_SERVER_NOT_READY_ERROR = -32000;

    // MCP Server response fields
    public static final String ACKNOWLEDGE_FIELD = "acknowledged";
    public static final String MCP_RESPONSE_FIELD = "mcp_response";
    public static final String ERROR_FIELD = "error";
    public static final String MESSAGE_FIELD = "message";
    public static final String ID_FIELD = "id";
    public static final String ERROR_CODE_FIELD = "error_code";
}
