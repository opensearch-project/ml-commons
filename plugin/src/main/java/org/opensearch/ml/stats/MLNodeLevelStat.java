/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

/**
 * ML commons has 4 levels of stats: cluster, node, algorithm and action.
 * This enum represents node level stats.
 */
public enum MLNodeLevelStat {
    ML_JVM_HEAP_USAGE,
    ML_EXECUTING_TASK_COUNT, // How many tasks are executing currently. If any task starts, then it will increase by 1,
                             // if the task finished then it will decrease by 0.
    ML_REQUEST_COUNT,
    ML_FAILURE_COUNT,
    ML_DEPLOYED_MODEL_COUNT,
    ML_CIRCUIT_BREAKER_TRIGGER_COUNT;

    public static MLNodeLevelStat from(String value) {
        try {
            return MLNodeLevelStat.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong ML node level stat");
        }
    }
}
