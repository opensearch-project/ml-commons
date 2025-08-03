/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the type of memory entry
 */
public enum MemoryType {
    RAW_MESSAGE("RAW_MESSAGE"),
    FACT("FACT");

    private final String value;

    MemoryType(String value) {
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
    public static MemoryType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (MemoryType type : MemoryType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid memory type: " + value + ". Must be either RAW_MESSAGE or FACT");
    }

    @Override
    public String toString() {
        return value;
    }
}
