/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the type of memory entry
 */
public enum MemoryStrategyType {
    SEMANTIC("SEMANTIC"),
    USER_PREFERENCE("USER_PREFERENCE"),
    SUMMARY("SUMMARY");

    private final String value;

    MemoryStrategyType(String value) {
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
    public static MemoryStrategyType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (MemoryStrategyType type : MemoryStrategyType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid memory type: " + value + ". Must be SEMANTIC, USER_PREFERENCE, or SUMMARY");
    }

    @Override
    public String toString() {
        return value;
    }
}
