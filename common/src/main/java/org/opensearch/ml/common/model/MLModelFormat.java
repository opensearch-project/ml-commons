/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.model;

public enum MLModelFormat {
    ONNX,
    TORCH_SCRIPT;

    public static MLModelFormat from(String value) {
        try {
            return MLModelFormat.valueOf(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong model format");
        }
    }
}
