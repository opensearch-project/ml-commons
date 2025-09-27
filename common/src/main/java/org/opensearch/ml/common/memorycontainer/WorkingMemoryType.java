/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the type of working memory entry
 */
public enum WorkingMemoryType {
    CONVERSATIONAL("conversational"),
    DATA("data");

    private final String value;

    WorkingMemoryType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse string value to WorkingMemoryType
     * @param value string representation of working memory type
     * @return corresponding WorkingMemoryType enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static WorkingMemoryType fromString(String value) {
        if (value == null) {
            return null;
        }

        for (WorkingMemoryType type : WorkingMemoryType.values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid working memory type: " + value + ". Must be one of: CONVERSATIONAL, DATA");
    }

    @Override
    public String toString() {
        return value;
    }
}
