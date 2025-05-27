/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.settings;

import static org.opensearch.remote.metadata.common.CommonValue.REMOTE_METADATA_ENDPOINT_KEY;
import static org.opensearch.remote.metadata.common.CommonValue.REMOTE_METADATA_REGION_KEY;
import static org.opensearch.remote.metadata.common.CommonValue.REMOTE_METADATA_SERVICE_NAME_KEY;
import static org.opensearch.remote.metadata.common.CommonValue.REMOTE_METADATA_TYPE_KEY;

import java.util.List;
import java.util.function.Function;

import org.opensearch.common.settings.Setting;
import org.opensearch.core.common.unit.ByteSizeUnit;
import org.opensearch.core.common.unit.ByteSizeValue;

import com.google.common.collect.ImmutableList;

public final class MLCommonsSettings {

    private MLCommonsSettings() {}

    public static final Setting<String> ML_COMMONS_TASK_DISPATCH_POLICY = Setting
        .simpleString("plugins.ml_commons.task_dispatch_policy", "round_robin", Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_MAX_MODELS_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_model_on_node", 10, 0, 10000, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_MAX_REGISTER_MODEL_TASKS_PER_NODE = Setting
        .intSetting(
            "plugins.ml_commons.max_register_model_tasks_per_node",
            10,
            0,
            10,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Integer> ML_COMMONS_MAX_BATCH_INFERENCE_TASKS = Setting
        .intSetting("plugins.ml_commons.max_batch_inference_tasks", 10, 0, 500, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_MAX_BATCH_INGESTION_TASKS = Setting
        .intSetting("plugins.ml_commons.max_batch_ingestion_tasks", 10, 0, 500, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_BATCH_INGESTION_BULK_SIZE = Setting
        .intSetting("plugins.ml_commons.batch_ingestion_bulk_size", 500, 100, 100000, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_MAX_DEPLOY_MODEL_TASKS_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_deploy_model_tasks_per_node", 10, 0, 10, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_MAX_ML_TASK_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_ml_task_per_node", 10, 0, 10000, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Boolean> ML_COMMONS_ONLY_RUN_ON_ML_NODE = Setting
        .boolSetting("plugins.ml_commons.only_run_on_ml_node", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_ENABLE_INHOUSE_PYTHON_MODEL = Setting
        .boolSetting("plugins.ml_commons.enable_inhouse_python_model", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS = Setting
        .intSetting(
            "plugins.ml_commons.sync_up_job_interval_in_seconds",
            10,
            0,
            86400,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Integer> ML_COMMONS_ML_TASK_TIMEOUT_IN_SECONDS = Setting
        .intSetting("plugins.ml_commons.ml_task_timeout_in_seconds", 600, 1, 86400, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Long> ML_COMMONS_MONITORING_REQUEST_COUNT = Setting
        .longSetting(
            "plugins.ml_commons.monitoring_request_count",
            100,
            0,
            10_000_000,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<String> ML_COMMONS_TRUSTED_URL_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.trusted_url_regex",
            "^(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Integer> ML_COMMONS_NATIVE_MEM_THRESHOLD = Setting
        .intSetting("plugins.ml_commons.native_memory_threshold", 90, 0, 100, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_JVM_HEAP_MEM_THRESHOLD = Setting
        .intSetting("plugins.ml_commons.jvm_heap_memory_threshold", 85, 0, 100, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<ByteSizeValue> ML_COMMONS_DISK_FREE_SPACE_THRESHOLD = Setting
        .byteSizeSetting(
            "plugins.ml_commons.disk_free_space_threshold",
            new ByteSizeValue(5L, ByteSizeUnit.GB),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<String> ML_COMMONS_EXCLUDE_NODE_NAMES = Setting
        .simpleString("plugins.ml_commons.exclude_nodes._name", Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Boolean> ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN = Setting
        .boolSetting("plugins.ml_commons.allow_custom_deployment_plan", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_MODEL_AUTO_DEPLOY_ENABLE = Setting
        .boolSetting("plugins.ml_commons.model_auto_deploy.enable", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_MODEL_AUTO_REDEPLOY_ENABLE = Setting
        .boolSetting("plugins.ml_commons.model_auto_redeploy.enable", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_MODEL_AUTO_REDEPLOY_LIFETIME_RETRY_TIMES = Setting
        .intSetting("plugins.ml_commons.model_auto_redeploy.lifetime_retry_times", 3, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Float> ML_COMMONS_MODEL_AUTO_REDEPLOY_SUCCESS_RATIO = Setting
        .floatSetting(
            "plugins.ml_commons.model_auto_redeploy_success_ratio",
            0.8f,
            0f,
            1f,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    // This setting is to enable/disable model url in model register API.
    public static final Setting<Boolean> ML_COMMONS_ALLOW_MODEL_URL = Setting
        .boolSetting("plugins.ml_commons.allow_registering_model_via_url", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_ALLOW_LOCAL_FILE_UPLOAD = Setting
        .boolSetting(
            "plugins.ml_commons.allow_registering_model_via_local_file",
            false,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    // This setting is to enable/disable Create Connector API and Register/Deploy/Predict Model APIs for remote models
    public static final Setting<Boolean> ML_COMMONS_REMOTE_INFERENCE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.remote_inference.enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_LOCAL_MODEL_ENABLED = Setting
        .boolSetting("plugins.ml_commons.local_model.enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED = Setting
        .boolSetting("plugins.ml_commons.model_access_control_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED = Setting
        .boolSetting("plugins.ml_commons.connector_access_control_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_OFFLINE_BATCH_INGESTION_ENABLED = Setting
        .boolSetting("plugins.ml_commons.offline_batch_ingestion_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_OFFLINE_BATCH_INFERENCE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.offline_batch_inference_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final String REKOGNITION_TRUST_ENDPOINT_REGEX = "^https://rekognition(-fips)?\\..*[a-z0-9-]\\.amazonaws\\.com$";

    public static final Setting<List<String>> ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX = Setting
        .listSetting(
            "plugins.ml_commons.trusted_connector_endpoints_regex",
            ImmutableList
                .of(
                    "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://api\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://api\\.openai\\.com/.*$",
                    "^https://api\\.cohere\\.ai/.*$",
                    "^https://api\\.deepseek\\.com/.*$",
                    "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://bedrock-agent-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://bedrock\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://textract\\..*[a-z0-9-]\\.amazonaws\\.com$",
                    "^https://comprehend\\..*[a-z0-9-]\\.amazonaws\\.com$",
                    REKOGNITION_TRUST_ENDPOINT_REGEX
                ),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    /**
     * Per PM's suggestion, remote model should be able to run on data or ML node by default.
     * But we should also keep local model run on ML node by default. So we still keep
     * "plugins.ml_commons.only_run_on_ml_node" as true, but only control local model.
     * Add more setting to provide granular level control on remote and local model.
     *
     * 1. Add "plugins.ml_commons.task_dispatcher.eligible_node_role.remote_model" which controls
     * only remote model can run on which node. Default value is ["data", "ml"] which means the
     * remote model can run on data node and ML node by default.
     * 2. Add "plugins.ml_commons.task_dispatcher.eligible_node_role.local_model" which controls
     * only remote model can run on which node. But we have "plugins.ml_commons.only_run_on_ml_node"
     * which controls the model can only run on ML node or not.
     * To provide BWC, for local model, 1/ if plugins.ml_commons.only_run_on_ml_node is true, we
     * will always run it on ML node. 2/ if plugins.ml_commons.only_run_on_ml_node is false, will
     * run model on nodes defined in plugins.ml_commons.task_dispatcher.eligible_node_role.local_model.
     */
    public static final Setting<List<String>> ML_COMMONS_REMOTE_MODEL_ELIGIBLE_NODE_ROLES = Setting
        .listSetting(
            "plugins.ml_commons.task_dispatcher.eligible_node_role.remote_model",
            ImmutableList.of("data", "ml"),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<List<String>> ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES = Setting
        .listSetting(
            "plugins.ml_commons.task_dispatcher.eligible_node_role.local_model",
            ImmutableList.of("data", "ml"),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    /** Feature Flag setting for conversational memory */
    public static final Setting<Boolean> ML_COMMONS_MEMORY_FEATURE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.memory_feature_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_MCP_CONNECTOR_ENABLED = Setting
        .boolSetting("plugins.ml_commons.mcp_connector_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final String ML_COMMONS_MCP_CONNECTOR_DISABLED_MESSAGE =
        "The MCP connector is not enabled. To enable, please update the setting " + ML_COMMONS_MCP_CONNECTOR_ENABLED.getKey();

    public static final Setting<Boolean> ML_COMMONS_MCP_SERVER_ENABLED = Setting
        .boolSetting("plugins.ml_commons.mcp_server_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final String ML_COMMONS_MCP_SERVER_DISABLED_MESSAGE =
        "The MCP server is not enabled. To enable, please update the setting " + ML_COMMONS_MCP_SERVER_ENABLED.getKey();

    // Feature flag for enabling search processors for Retrieval Augmented Generation using OpenSearch and Remote Inference.
    public static final Setting<Boolean> ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED = Setting
        .boolSetting("plugins.ml_commons.rag_pipeline_feature_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    // This setting is to enable/disable agent related API register/execute/delete/get/search agent.
    public static final Setting<Boolean> ML_COMMONS_AGENT_FRAMEWORK_ENABLED = Setting
        .boolSetting("plugins.ml_commons.agent_framework_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_CONNECTOR_PRIVATE_IP_ENABLED = Setting
        .boolSetting("plugins.ml_commons.connector.private_ip_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<List<String>> ML_COMMONS_REMOTE_JOB_STATUS_FIELD = Setting
        .listSetting(
            "plugins.ml_commons.remote_job.status_field",
            ImmutableList
                .of(
                    "status", // openai, bedrock, cohere
                    "Status",
                    "TransformJobStatus" // sagemaker
                ),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<String> ML_COMMONS_REMOTE_JOB_STATUS_COMPLETED_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.remote_job.status_regex.completed",
            "(complete|completed|partiallyCompleted)",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );
    public static final Setting<String> ML_COMMONS_REMOTE_JOB_STATUS_CANCELLED_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.remote_job.status_regex.cancelled",
            "(stopped|cancelled)",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );
    public static final Setting<String> ML_COMMONS_REMOTE_JOB_STATUS_CANCELLING_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.remote_job.status_regex.cancelling",
            "(stopping|cancelling)",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );
    public static final Setting<String> ML_COMMONS_REMOTE_JOB_STATUS_EXPIRED_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.remote_job.status_regex.expired",
            "(expired|timeout)",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<String> ML_COMMONS_REMOTE_JOB_STATUS_FAILED_REGEX = Setting
        .simpleString(
            "plugins.ml_commons.remote_job.status_regex.failed",
            "(failed)",
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Boolean> ML_COMMONS_CONTROLLER_ENABLED = Setting
        .boolSetting("plugins.ml_commons.controller_enabled", true, Setting.Property.NodeScope, Setting.Property.Dynamic);

    // This flag is the determine whether we need to check downstream task before deleting a model.
    public static final Setting<Boolean> ML_COMMONS_SAFE_DELETE_WITH_USAGE_CHECK = Setting
        .boolSetting("plugins.ml_commons.safe_delete_model", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    /**
     * Indicates whether multi-tenancy is enabled in ML Commons.
     *
     * This is a static setting that must be configured before starting OpenSearch. It can be set in the following ways, in priority order:
     *
     * <ol>
     *   <li>As a command-line argument using the <code>-E</code> flag (this overrides other options):
     *       <pre>
     *       ./bin/opensearch -Eplugins.ml_commons.multi_tenancy_enabled=true
     *       </pre>
     *   </li>
     *   <li>As a system property using <code>OPENSEARCH_JAVA_OPTS</code> (this overrides <code>opensearch.yml</code>):
     *       <pre>
     *       export OPENSEARCH_JAVA_OPTS="-Dplugins.ml_commons.multi_tenancy_enabled=true"
     *       ./bin/opensearch
     *       </pre>
     *       Or inline when starting OpenSearch:
     *       <pre>
     *       OPENSEARCH_JAVA_OPTS="-Dplugins.ml_commons.multi_tenancy_enabled=true" ./bin/opensearch
     *       </pre>
     *   </li>
     *   <li>In the <code>opensearch.yml</code> configuration file:
     *       <pre>
     *       plugins.ml_commons.multi_tenancy_enabled: true
     *       </pre>
     *   </li>
     * </ol>
     *
     * After setting this option, a full cluster restart is required for the changes to take effect.
     */
    public static final Setting<Boolean> ML_COMMONS_MULTI_TENANCY_ENABLED = Setting
        .boolSetting("plugins.ml_commons.multi_tenancy_enabled", false, Setting.Property.NodeScope);

    /** This setting sets the remote metadata type */
    public static final Setting<String> REMOTE_METADATA_TYPE = Setting
        .simpleString("plugins.ml_commons." + REMOTE_METADATA_TYPE_KEY, Setting.Property.NodeScope, Setting.Property.Final);

    /** This setting sets the remote metadata endpoint */
    public static final Setting<String> REMOTE_METADATA_ENDPOINT = Setting
        .simpleString("plugins.ml_commons." + REMOTE_METADATA_ENDPOINT_KEY, Setting.Property.NodeScope, Setting.Property.Final);

    /** This setting sets the remote metadata region */
    public static final Setting<String> REMOTE_METADATA_REGION = Setting
        .simpleString("plugins.ml_commons." + REMOTE_METADATA_REGION_KEY, Setting.Property.NodeScope, Setting.Property.Final);

    /** This setting sets the remote metadata service name */
    public static final Setting<String> REMOTE_METADATA_SERVICE_NAME = Setting
        .simpleString("plugins.ml_commons." + REMOTE_METADATA_SERVICE_NAME_KEY, Setting.Property.NodeScope, Setting.Property.Final);
}
