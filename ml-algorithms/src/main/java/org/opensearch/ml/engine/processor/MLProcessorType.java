/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.processor;

public enum MLProcessorType {
    CONDITIONAL("CONDITIONAL"),
    EXTRACT_JSON("EXTRACT_JSON"),
    JSONPATH_FILTER("JSONPATH_FILTER"),
    PROCESS_AND_SET("PROCESS_AND_SET"),
    REGEX_CAPTURE("REGEX_CAPTURE"),
    REGEX_REPLACE("REGEX_REPLACE"),
    REMOVE_JSONPATH("REMOVE_JSONPATH"),
    SET_FIELD("SET_FIELD"),
    TO_STRING("TO_STRING");

    private final String value;

    MLProcessorType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse string value to MemoryType
     * @param value string representation of memory type
     * @return corresponding MemoryType enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static MLProcessorType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (MLProcessorType type : MLProcessorType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid ML processor type: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
