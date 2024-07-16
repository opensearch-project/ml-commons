/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.io.IOException;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.Writeable;

public class ColumnValueReader implements Writeable.Reader<ColumnValue> {
    @Override
    public ColumnValue read(StreamInput in) throws IOException {
        ColumnType columnType = in.readEnum(ColumnType.class);
        switch (columnType) {
            case SHORT:
                return new ShortValue(in.readShort());
            case INTEGER:
                return new IntValue(in.readInt());
            case LONG:
                return new LongValue(in.readLong());
            case DOUBLE:
                return new DoubleValue(in.readDouble());
            case STRING:
                return new StringValue(in.readString());
            case BOOLEAN:
                return new BooleanValue(in.readBoolean());
            case FLOAT:
                return new FloatValue(in.readFloat());
            case NULL:
                return new NullValue();
            default:
                throw new IllegalArgumentException("unknown type:" + columnType);

        }
    }
}
