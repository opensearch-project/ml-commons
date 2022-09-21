/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

public enum MLModelState {
    UPLOADING,
    UPLOADED,
    LOADED,
    UNLOADED;

    public static MLModelState from(String value) {
        try {
            return MLModelState.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong model format");
        }
    }
}