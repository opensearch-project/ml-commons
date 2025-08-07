/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

/**
 * Enum representing memory operation events
 */
public enum MemoryEvent {
    ADD("ADD"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
    NONE("NONE");

    private final String value;

    MemoryEvent(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static MemoryEvent fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Memory event value cannot be null");
        }

        for (MemoryEvent event : MemoryEvent.values()) {
            if (event.value.equalsIgnoreCase(value)) {
                return event;
            }
        }
        throw new IllegalArgumentException("Unknown memory event: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
