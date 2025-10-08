/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.agent;

import java.util.Locale;

/**
 * Enum for supported model provider types
 */
public enum ModelProviderType {
    BEDROCK_CONVERSE("bedrock/converse"),
    OPENAI("openai"),
    AZURE_OPENAI("azure/openai");

    private final String value;

    ModelProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ModelProviderType from(String value) {
        for (ModelProviderType type : ModelProviderType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown model provider type: " + value);
    }
}