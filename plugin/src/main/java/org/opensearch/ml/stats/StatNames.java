/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.HashSet;
import java.util.Set;

import lombok.Getter;

/**
 * Enum containing names of all stats
 */
public enum StatNames {
    ML_EXECUTING_TASK_COUNT("ml_executing_task_count");

    @Getter
    private String name;

    StatNames(String name) {
        this.name = name;
    }

    /**
     * Get set of stat names
     *
     * @return set of stat names
     */
    public static Set<String> getNames() {
        Set<String> names = new HashSet<>();

        for (StatNames statName : StatNames.values()) {
            names.add(statName.getName());
        }
        return names;
    }
}
