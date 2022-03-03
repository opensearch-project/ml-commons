/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.Locale;

import org.opensearch.ml.common.parameter.FunctionName;

/**
 * Enum containing names of all stats
 */
public class StatNames {
    public static String ML_EXECUTING_TASK_COUNT = "ml_executing_task_count";
    public static String ML_TOTAL_REQUEST_COUNT = "ml_total_request_count";
    public static String ML_TOTAL_FAILURE_COUNT = "ml_total_failure_count";
    public static String ML_TOTAL_MODEL_COUNT = "ml_total_model_count";

    public static String requestCountStat(FunctionName functionName, ActionName actionName) {
        return String.format("ml_%s_%s_request_count", functionName, actionName, Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    public static String failureCountStat(FunctionName functionName, ActionName actionName) {
        return String.format("ml_%s_%s_failure_count", functionName, actionName, Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    public static String executingRequestCountStat(FunctionName functionName, ActionName actionName) {
        return String.format("ml_%s_%s_executing_request_count", functionName, actionName, Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    public static String modelCountStat(FunctionName functionName) {
        return String.format("ml_%s_model_count", functionName, Locale.ROOT).toLowerCase(Locale.ROOT);
    }
}
