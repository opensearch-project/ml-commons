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

import lombok.Getter;

public enum DataFrameType {
    DEFAULT("default");

    @Getter
    private final String name;

    DataFrameType(String name) {
        this.name = name;
    }

    public String toString() {
        return name;
    }

    public static DataFrameType fromString(String name) {
        for (DataFrameType e : DataFrameType.values()) {
            if (e.name.equals(name)) return e;
        }
        return null;
    }
}
