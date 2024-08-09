/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

public enum ColumnType {
    STRING,
    SHORT,
    INTEGER,
    LONG,
    DOUBLE,
    BOOLEAN,
    FLOAT,
    NULL;

    public static ColumnType from(Object object) {
        if (object instanceof Short) {
            return SHORT;
        }

        if (object instanceof Integer) {
            return INTEGER;
        }

        if (object instanceof Long) {
            return LONG;
        }

        if (object instanceof String) {
            return STRING;
        }

        if (object instanceof Double) {
            return DOUBLE;
        }

        if (object instanceof Boolean) {
            return BOOLEAN;
        }

        if (object instanceof Float) {
            return FLOAT;
        }

        throw new IllegalArgumentException("unsupported type:" + object.getClass().getName());
    }
}
