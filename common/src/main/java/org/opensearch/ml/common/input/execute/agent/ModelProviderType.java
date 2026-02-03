/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

/**
 * Enum for supported model provider types
 */
public enum ModelProviderType {
    BEDROCK_CONVERSE("bedrock/converse"),
    GEMINI_V1BETA_GENERATE_CONTENT("gemini/v1beta/generatecontent");

    private final String value;

    ModelProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ModelProviderType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Model provider type cannot be null");
        }

        for (ModelProviderType type : ModelProviderType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown model provider type. Supported types: bedrock/converse, gemini/v1beta/generatecontent");
    }
}
