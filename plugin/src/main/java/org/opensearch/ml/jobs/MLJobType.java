/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.jobs;

public enum MLJobType {
    STATS_COLLECTOR("Job to collect static metrics and push to Metrics Registry"),
    BATCH_TASK_UPDATE("Job to poll and update status of running batch prediction tasks for remote models");

    private final String description;

    MLJobType(String description) {
        this.description = description;
    }
}
