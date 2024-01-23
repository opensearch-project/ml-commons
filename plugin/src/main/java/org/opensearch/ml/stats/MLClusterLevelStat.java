/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

/**
 * ML commons has 4 levels of stats: cluster, node, algorithm and action.
 * This enum represents cluster level stats.
 */
public enum MLClusterLevelStat {
    ML_MODEL_INDEX_STATUS,
    ML_CONNECTOR_INDEX_STATUS,
    ML_CONFIG_INDEX_STATUS,
    ML_TASK_INDEX_STATUS,
    ML_CONTROLLER_INDEX_STATUS,
    ML_MODEL_COUNT,
    ML_CONNECTOR_COUNT;

    public static MLClusterLevelStat from(String value) {
        try {
            return MLClusterLevelStat.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong ML cluster level stat");
        }
    }
}
