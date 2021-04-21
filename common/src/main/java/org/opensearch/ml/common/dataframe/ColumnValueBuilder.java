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

import java.util.Objects;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ColumnValueBuilder {

    /**
     * build column value based on input object
     * @param object input object
     * @return ColumnValue
     */
    public ColumnValue build(Object object) {
        if(Objects.isNull(object)) {
            return new NullValue();
        }

        if(object instanceof Integer) {
            return new IntValue((Integer)object);
        }

        if(object instanceof String) {
            return new StringValue((String)object);
        }

        if(object instanceof Double) {
            return new DoubleValue((Double)object);
        }

        if(object instanceof Boolean) {
            return new BooleanValue((Boolean)object);
        }

        throw new IllegalArgumentException("unsupported type:" + object.getClass().getName());
    }
}
