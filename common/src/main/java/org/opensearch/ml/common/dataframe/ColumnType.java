/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.dataframe;

public enum ColumnType {
    STRING,
    INTEGER,
    DOUBLE,
    BOOLEAN,
    NULL;

    public static ColumnType fromString(String type) {
        switch (type) {
            case "STRING":
                return STRING;
            case "INTEGER":
                return INTEGER;
            case "DOUBLE":
                return DOUBLE;
            case "BOOLEAN":
                return BOOLEAN;
            case "NULL":
                return NULL;
            default:
                return  null;
        }
    }

    public static ColumnType from(Object object) {
        if(object instanceof Integer) {
            return INTEGER;
        }

        if(object instanceof String) {
            return STRING;
        }

        if(object instanceof Double) {
            return DOUBLE;
        }

        if(object instanceof Boolean) {
            return BOOLEAN;
        }

        throw new IllegalArgumentException("unsupported type:" + object.getClass().getName());
    }
}
