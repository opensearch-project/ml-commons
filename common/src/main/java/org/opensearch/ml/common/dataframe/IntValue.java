/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@ToString
public class IntValue implements ColumnValue {
    int value;

    @Override
    public ColumnType columnType() {
        return ColumnType.INTEGER;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return Integer.valueOf(value).doubleValue();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
        out.writeInt(value);
    }
}
