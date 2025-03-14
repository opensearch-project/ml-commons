package org.opensearch.ml.jobs;

// todo: link job type to processor like a factory
public enum MLJobType {
    STATS_COLLECTOR("Job to collect static metrics and push to Metrics Registry"),
    BATCH_TASK_UPDATE("Job to do xyz");

    private final String description;

    MLJobType(String description) {
        this.description = description;
    }
}
