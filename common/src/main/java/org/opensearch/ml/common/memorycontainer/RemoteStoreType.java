/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.memorycontainer;

import java.util.Arrays;

public enum RemoteStoreType {
    OPENSEARCH("opensearch"),
    AOSS("aoss");

    private final String value;

    RemoteStoreType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static RemoteStoreType fromString(String value) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.toLowerCase();
        for (RemoteStoreType type : RemoteStoreType.values()) {
            if (type.value.equalsIgnoreCase(normalizedValue)) {
                return type;
            }
        }

        throw new IllegalArgumentException(
            "Invalid memory type: " + value + ". Must be one of: " + Arrays.toString(RemoteStoreType.values())
        );
    }

    @Override
    public String toString() {
        return value;
    }
}
