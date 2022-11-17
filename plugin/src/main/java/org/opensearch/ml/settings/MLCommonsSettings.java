/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.settings;

import org.opensearch.common.settings.Setting;

public final class MLCommonsSettings {

    private MLCommonsSettings() {}

    public static final Setting<String> ML_COMMONS_TASK_DISPATCH_POLICY = Setting
        .simpleString("plugins.ml_commons.task_dispatch_policy", "round_robin", Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_MAX_MODELS_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_model_on_node", 10, 0, 1000, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_MAX_LOAD_MODEL_TASKS_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_load_model_tasks_per_node", 1, 0, 10, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Boolean> ML_COMMONS_ONLY_RUN_ON_ML_NODE = Setting
        .boolSetting("plugins.ml_commons.only_run_on_ml_node", false, Setting.Property.NodeScope, Setting.Property.Dynamic);
    public static final Setting<Integer> ML_COMMONS_SYNC_UP_JOB_INTERVAL_IN_SECONDS = Setting
        .intSetting(
            "plugins.ml_commons.sync_up_job_interval_in_seconds",
            1,
            0,
            86400,
            Setting.Property.NodeScope,
            Setting.Property.Dynamic
        );

    public static final Setting<Long> ML_COMMONS_MONITORING_REQUEST_COUNT = Setting
        .longSetting("plugins.ml_commons.monitoring_request_count", 100, 0, 100_000, Setting.Property.NodeScope, Setting.Property.Dynamic);

    public static final Setting<Integer> ML_COMMONS_MAX_UPLOAD_TASKS_PER_NODE = Setting
        .intSetting("plugins.ml_commons.max_upload_model_tasks_per_node", 1, 0, 10, Setting.Property.NodeScope, Setting.Property.Dynamic);
}
