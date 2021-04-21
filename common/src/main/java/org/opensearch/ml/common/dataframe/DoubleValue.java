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

import java.io.IOException;

import org.opensearch.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@ToString
public class DoubleValue implements ColumnValue {
    double value;

    @Override
    public ColumnType columnType() {
        return ColumnType.DOUBLE;
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeEnum(columnType());
        out.writeDouble(value);
    }
}
