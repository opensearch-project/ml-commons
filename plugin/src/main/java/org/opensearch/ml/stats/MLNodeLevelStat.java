/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.Set;

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
    ML_CIRCUIT_BREAKER_TRIGGER_COUNT,
    ML_OAUTH2_TOKEN_REQUEST_COUNT, // Token requests the node's OAuth2 token cache started.
    ML_OAUTH2_TOKEN_FAILURE_COUNT, // Started token requests that failed.
    ML_OAUTH2_TOKEN_CACHE_HIT_COUNT, // Requests served a cached OAuth2 token.
    ML_OAUTH2_UNAUTHORIZED_RESPONSE_COUNT; // 401 answers to requests that carried an OAuth2 token.

    /** The stats that came with connector auth_config; a node of an earlier release does not know them. */
    public static final Set<MLNodeLevelStat> CONNECTOR_AUTH_STATS = Set
        .of(
            ML_OAUTH2_TOKEN_REQUEST_COUNT,
            ML_OAUTH2_TOKEN_FAILURE_COUNT,
            ML_OAUTH2_TOKEN_CACHE_HIT_COUNT,
            ML_OAUTH2_UNAUTHORIZED_RESPONSE_COUNT
        );

    public static MLNodeLevelStat from(String value) {
        try {
            return MLNodeLevelStat.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong ML node level stat");
        }
    }
}
