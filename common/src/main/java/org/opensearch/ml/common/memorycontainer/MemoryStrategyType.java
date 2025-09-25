/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the type of memory strategy
 */
public enum MemoryStrategyType {
    SEMANTIC("SEMANTIC");

    private final String value;

    MemoryStrategyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse string value to MemoryStrategyType
     * @param value string representation of memory strategy type
     * @return corresponding MemoryStrategyType enum
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

        throw new IllegalArgumentException("Invalid memory strategy type: " + value + ". Must be: SEMANTIC");
    }

    @Override
    public String toString() {
        return value;
    }
}
