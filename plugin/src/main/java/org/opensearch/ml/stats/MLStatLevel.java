/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

public enum MLStatLevel {
    CLUSTER,
    NODE,
    ALGORITHM,
    MODEL,
    ACTION;

    public static MLStatLevel from(String value) {
        try {
            return MLStatLevel.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong ML stat level");
        }
    }
}
