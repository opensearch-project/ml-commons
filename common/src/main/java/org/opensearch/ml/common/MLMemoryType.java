/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common;

import java.util.Locale;

public enum MLMemoryType {
    CONVERSATION_INDEX,
    AGENTIC_MEMORY;

    public static MLMemoryType from(String value) {
        if (value != null) {
            try {
                return MLMemoryType.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                throw new IllegalArgumentException("Wrong Memory type");
            }
        }
        return null;
    }
}
