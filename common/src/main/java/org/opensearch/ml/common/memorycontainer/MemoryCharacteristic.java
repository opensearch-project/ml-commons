/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

/**
 * Enum representing the characteristic of a memory entry
 */
public enum MemoryCharacteristic {
    SHORT_TERM("SHORT_TERM"),
    LONG_TERM("LONG_TERM");

    private final String value;

    MemoryCharacteristic(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /**
     * Parse string value to MemoryCharacteristic
     * @param value string representation of memory characteristic
     * @return corresponding MemoryCharacteristic enum
     * @throws IllegalArgumentException if value is invalid
     */
    public static MemoryCharacteristic fromString(String value) {
        if (value == null) {
            return null;
        }

        for (MemoryCharacteristic characteristic : MemoryCharacteristic.values()) {
            if (characteristic.value.equalsIgnoreCase(value)) {
                return characteristic;
            }
        }

        throw new IllegalArgumentException("Invalid memory characteristic: " + value + ". Must be either SHORT_TERM or LONG_TERM");
    }

    @Override
    public String toString() {
        return value;
    }
}
