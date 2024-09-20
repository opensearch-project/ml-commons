/*
 *
 *  * Copyright OpenSearch Contributors
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */

package org.opensearch.ml.common;

import java.util.HashMap;
import java.util.Map;

import lombok.Getter;

public enum AccessMode {
    PUBLIC("public"),
    PRIVATE("private"),
    RESTRICTED("restricted");

    @Getter
    private String value;

    AccessMode(String value) {
        this.value = value;
    }

    private static final Map<String, AccessMode> cache = new HashMap<>();

    static {
        for (AccessMode modelAccessMode : values()) {
            cache.put(modelAccessMode.value, modelAccessMode);
        }
    }

    public static AccessMode from(String value) {
        try {
            return cache.get(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Wrong access value");
        }
    }
}
