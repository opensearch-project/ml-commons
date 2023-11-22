/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.output.model;

public enum MLResultDataType {
    FLOAT32(Format.FLOATING, 4),
    FLOAT64(Format.FLOATING, 8),
    FLOAT16(Format.FLOATING, 2),
    UINT8(Format.UINT, 1),
    INT32(Format.INT, 4),
    INT8(Format.INT, 1),
    INT64(Format.INT, 8),
    BOOLEAN(Format.BOOLEAN, 1),
    UNKNOWN(Format.UNKNOWN, 0),
    STRING(Format.STRING, -1);

    /** The general data type format categories. */
    public enum Format {
        FLOATING,
        UINT,
        INT,
        BOOLEAN,
        STRING,
        UNKNOWN
    }

    private Format format;
    private int numOfBytes;

    MLResultDataType(Format format, int numOfBytes) {
        this.format = format;
        this.numOfBytes = numOfBytes;
    }

    /**
     * Checks whether it is a floating data type.
     *
     * @return whether it is a floating data type
     */
    public boolean isFloating() {
        return format == Format.FLOATING;
    }

    /**
     * Checks whether it is an integer data type.
     *
     * @return whether it is an integer type
     */
    public boolean isInteger() {
        return format == Format.UINT || format == Format.INT;
    }

    /**
     * Checks whether it is a boolean data type.
     *
     * @return whether it is a boolean data type
     */
    public boolean isBoolean() {
        return format == Format.BOOLEAN;
    }
}
