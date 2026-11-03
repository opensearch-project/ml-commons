/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enum representing the types of memory in the Agentic Memory system.
 * Each memory container can have up to four types of memory indices.
 */
public enum MemoryType {
    /**
     * Session index - tracks conversation sessions and their metadata
     */
    SESSIONS("sessions", "sessions"),

    /**
     * Working memory index - stores immediate messages and data
     */
    WORKING("working", "working"),

    /**
     * Long-term memory index - stores extracted facts and persistent knowledge
     */
    LONG_TERM("long-term", "long-term"),

    /**
     * History index - complete audit trail of memory operations
     */
    HISTORY("history", "history");

    private final String value;
    private final String indexSuffix;

    MemoryType(String value, String indexSuffix) {
        this.value = value;
        this.indexSuffix = indexSuffix;
    }

    /**
     * Get the string value used in APIs and URLs
     * @return the string representation (e.g., "sessions", "working")
     */
    public String getValue() {
        return value;
    }

    /**
     * Get the suffix used for index naming
     * @return the index suffix (e.g., "sessions", "working")
     */
    public String getIndexSuffix() {
        return indexSuffix;
    }

    /**
     * Check if this memory type can be disabled
     * @return true if this type supports being disabled (SESSIONS and HISTORY)
     */
    public boolean isDisableable() {
        return this == SESSIONS || this == HISTORY;
    }

    /**
     * Construct the full index name with the given prefix
     * @param prefix the index prefix
     * @return the full index name
     */
    public String toIndexName(String prefix) {
        return prefix + indexSuffix;
    }

    /**
     * Parse a string value to MemoryType
     * @param value string representation of memory type
     * @return corresponding MemoryType enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static MemoryType fromString(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.toLowerCase();
        for (MemoryType type : MemoryType.values()) {
            if (type.value.equalsIgnoreCase(normalizedValue)) {
                return type;
            }
        }

        throw new IllegalArgumentException("Invalid memory type: " + value + ". Must be one of: " + getAllValuesAsString());
    }

    /**
     * Check if a string value is a valid memory type
     * @param value the string to check
     * @return true if the value represents a valid memory type
     */
    public static boolean isValid(String value) {
        try {
            return fromString(value) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get all valid string values for error messages
     * @return list of all valid string values
     */
    public static List<String> getAllValues() {
        return Arrays.stream(values()).map(MemoryType::getValue).collect(Collectors.toList());
    }

    /**
     * Get comma-separated string of all valid values for error messages
     * @return comma-separated string of all valid values
     */
    public static String getAllValuesAsString() {
        return String.join(", ", getAllValues());
    }

    @Override
    public String toString() {
        return value;
    }
}
