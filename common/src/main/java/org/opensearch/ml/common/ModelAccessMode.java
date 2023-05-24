/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public enum ModelAccessMode {
    PUBLIC("public"),
    PRIVATE("private"),
    RESTRICTED("restricted");

    @Getter
    private String value;

    ModelAccessMode(String value) {
        this.value = value;
    }

    private static final Map<String, ModelAccessMode> cache = new HashMap<>();

    static {
        for (ModelAccessMode modelAccessMode : values()) {
            cache.put(modelAccessMode.value, modelAccessMode);
        }
    }

    public static ModelAccessMode from(String value) {
        try {
            return cache.get(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong access value");
        }
    }
}
