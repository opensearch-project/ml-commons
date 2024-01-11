/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.settings;

import java.util.List;
import java.util.function.Function;

import org.opensearch.common.settings.Setting;
import org.opensearch.ml.common.conversation.ConversationalIndexConstants;
import org.opensearch.searchpipelines.questionanswering.generative.GenerativeQAProcessorConstants;

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

    public static final Setting<String> ML_COMMONS_EXCLUDE_NODE_NAMES = Setting
        .simpleString("plugins.ml_commons.exclude_nodes._name", Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Boolean> ML_COMMONS_ALLOW_CUSTOM_DEPLOYMENT_PLAN = Setting
        .boolSetting("plugins.ml_commons.allow_custom_deployment_plan", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

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

    public static final Setting<Boolean> ML_COMMONS_MODEL_ACCESS_CONTROL_ENABLED = Setting
        .boolSetting("plugins.ml_commons.model_access_control_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_CONNECTOR_ACCESS_CONTROL_ENABLED = Setting
        .boolSetting("plugins.ml_commons.connector_access_control_enabled", false, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<List<String>> ML_COMMONS_TRUSTED_CONNECTOR_ENDPOINTS_REGEX = Setting
        .listSetting(
            "plugins.ml_commons.trusted_connector_endpoints_regex",
            List
                .of(
                    "^https://runtime\\.sagemaker\\..*[a-z0-9-]\\.amazonaws\\.com/.*$",
                    "^https://api\\.openai\\.com/.*$",
                    "^https://api\\.cohere\\.ai/.*$",
                    "^https://bedrock-runtime\\..*[a-z0-9-]\\.amazonaws\\.com/.*$"
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
            List.of("data", "ml"),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<List<String>> ML_COMMONS_LOCAL_MODEL_ELIGIBLE_NODE_ROLES = Setting
        .listSetting(
            "plugins.ml_commons.task_dispatcher.eligible_node_role.local_model",
            List.of("data", "ml"),
            Function.identity(),
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Boolean> ML_COMMONS_MEMORY_FEATURE_ENABLED = ConversationalIndexConstants.ML_COMMONS_MEMORY_FEATURE_ENABLED;

    // Feature flag for enabling search processors for Retrieval Augmented Generation using OpenSearch and Remote Inference.
    public static final Setting<Boolean> ML_COMMONS_RAG_PIPELINE_FEATURE_ENABLED =
        GenerativeQAProcessorConstants.RAG_PIPELINE_FEATURE_ENABLED;
}
