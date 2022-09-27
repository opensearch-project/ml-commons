/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

public enum MLModelTaskType {
    TEXT_EMBEDDING;

    public static MLModelTaskType from(String value) {
        try {
            return MLModelTaskType.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong model task type");
        }
    }
}
