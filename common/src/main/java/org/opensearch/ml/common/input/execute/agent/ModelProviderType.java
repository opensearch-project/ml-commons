/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.input.execute.agent;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enum for supported model provider types
 */
public enum ModelProviderType {
    BEDROCK_CONVERSE("bedrock/converse"),
    GEMINI_V1BETA_GENERATE_CONTENT("gemini/v1beta/generatecontent"),
    OPENAI_V1_CHAT_COMPLETIONS("openai/v1/chat/completions");

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
        String supportedTypes = Stream.of(ModelProviderType.values()).map(ModelProviderType::getValue).collect(Collectors.joining(", "));
        throw new IllegalArgumentException("Unknown model provider type. Supported types: " + supportedTypes);
    }
}
