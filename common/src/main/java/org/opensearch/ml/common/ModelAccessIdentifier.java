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

public enum ModelAccessIdentifier {
    PUBLIC("public"),
    PRIVATE("private"),
    RESTRICTED("restricted");

    @Getter
    private String value;

    ModelAccessIdentifier(String value) {
        this.value = value;
    }

    private static final Map<String, ModelAccessIdentifier> cache = new HashMap<>();

    static {
        for (ModelAccessIdentifier modelAccessIdentifier : values()) {
            cache.put(modelAccessIdentifier.value, modelAccessIdentifier);
        }
    }

    public static ModelAccessIdentifier from(String value) {
        try {
            return cache.get(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong access value");
        }
    }
}
