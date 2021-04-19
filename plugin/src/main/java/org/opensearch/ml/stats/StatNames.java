/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.stats;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

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
