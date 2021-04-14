/*
 *  Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License").
 *  You may not use this file except in compliance with the License.
 *  A copy of the License is located at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

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
