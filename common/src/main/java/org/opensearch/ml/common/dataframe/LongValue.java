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
public class LongValue implements ColumnValue {
    long value;

    @Override
    public ColumnType columnType() {
        return ColumnType.LONG;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return Long.valueOf(value).doubleValue();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
        out.writeLong(value);
    }
}
