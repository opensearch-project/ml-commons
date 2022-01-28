/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import org.opensearch.common.io.stream.StreamOutput;

import java.io.IOException;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@ToString
public class FloatValue implements ColumnValue {
    float value;

    @Override
    public ColumnType columnType() {
        return ColumnType.FLOAT;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return Float.valueOf(value).doubleValue();
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
        out.writeFloat(value);
    }
}
