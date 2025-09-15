/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the type of memory entry
 */
public enum ShortTermMemoryType {
    CONVERSATION("conversation"),
    DATA("data");

    private final String value;

    ShortTermMemoryType(String value) {
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
    public static ShortTermMemoryType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (ShortTermMemoryType type : ShortTermMemoryType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid short memory type: " + value + ". Must be one of: CONVERSATION, DATA");
    }

    @Override
    public String toString() {
        return value;
    }
}
