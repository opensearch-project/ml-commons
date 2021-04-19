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
