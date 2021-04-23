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
import java.util.Arrays;
import java.util.Iterator;

import lombok.Getter;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;

import lombok.AccessLevel;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class Row implements Iterable<ColumnValue>, Writeable {
    ColumnValue[] values;

    Row(int size) {
        this.values = new ColumnValue[size];
        Arrays.fill(this.values, new NullValue());
    }

    Row(StreamInput input) throws IOException {
        this.values = input.readArray(new ColumnValueReader(), ColumnValue[]::new);
    }

    void setValue(int index, ColumnValue value) {
        if(index < 0 || index > size() - 1) {
            throw new IllegalArgumentException("index is out of scope, index:" + index + "; row size:" + size());
        }
        this.values[index] = value;
    }

    public ColumnValue getValue(int index) {
        if(index < 0 || index > size() - 1) {
            throw new IllegalArgumentException("index is out of scope, index:" + index + "; row size:" + size());
        }
        return this.values[index];
    }

    @Override
    public Iterator<ColumnValue> iterator() {
        return Arrays.stream(this.values).iterator();
    }

    public int size() {
        return values.length;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeArray(values);
    }
}
