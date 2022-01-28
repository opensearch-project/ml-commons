/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import lombok.Getter;

/**
 * Enum containing names of all internal stats which will not be returned
 * in ML stats REST API.
 */
public enum InternalStatNames {
    JVM_HEAP_USAGE("jvm_heap_usage");

    @Getter
    private String name;

    InternalStatNames(String name) {
        this.name = name;
    }
}
