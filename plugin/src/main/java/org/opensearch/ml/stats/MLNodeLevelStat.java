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
    ML_EXECUTING_TASK_COUNT,
    ML_REQUEST_COUNT,
    ML_FAILURE_COUNT,
    ML_TOTAL_MODEL_COUNT,
    ML_CIRCUIT_BREAKER_TRIGGER_COUNT;

    public static MLNodeLevelStat from(String value) {
        try {
            return MLNodeLevelStat.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong ML node level stat");
        }
    }
}
