/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.opensearch.core.xcontent.XContentParserUtils.ensureExpectedToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

import lombok.AccessLevel;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class Row implements Iterable<ColumnValue>, Writeable, ToXContentObject {
    ColumnValue[] values;

    Row(int size) {
        this.values = new ColumnValue[size];
        Arrays.fill(this.values, new NullValue());
    }

    public Row(ColumnValue[] values) {
        this.values = values;
    }

    Row(StreamInput input) throws IOException {
        this.values = input.readArray(new ColumnValueReader(), ColumnValue[]::new);
    }

    void setValue(int index, ColumnValue value) {
        if (index < 0 || index > size() - 1) {
            throw new IllegalArgumentException("index is out of scope, index:" + index + "; row size:" + size());
        }
        this.values[index] = value;
    }

    public ColumnValue getValue(int index) {
        if (index < 0 || index > size() - 1) {
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

    Row remove(int removedIndex) {
        if (removedIndex < 0 || removedIndex >= values.length) {
            throw new IllegalArgumentException("removed index can't be negative or bigger than row's values length:" + values.length);
        }
        ColumnValue[] newValues = new ColumnValue[Math.max(values.length - 1, 0)];
        int index = 0;
        for (int i = 0; i < values.length && i != removedIndex; i++) {
            newValues[index++] = values[i];
        }

        return new Row(newValues);
    }

    Row select(int[] columns) {
        ColumnValue[] newValues = new ColumnValue[columns.length];
        int index = 0;
        for (int col : columns) {
            newValues[index++] = values[col];
        }

        return new Row(newValues);
    }

    public static Row parse(XContentParser parser) throws IOException {
        List<ColumnValue> values = new ArrayList<>();

        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
        while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
            String fieldName = parser.currentName();
            parser.nextToken();

            switch (fieldName) {
                case "values":
                    ensureExpectedToken(XContentParser.Token.START_ARRAY, parser.currentToken(), parser);
                    while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                        ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.currentToken(), parser);
                        if (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                            String columnTypeField = parser.currentName();
                            if (!"column_type".equals(columnTypeField)) {
                                throw new IllegalArgumentException(
                                    "wrong column type, expect column_type field but got " + columnTypeField
                                );
                            }
                            parser.nextToken();
                            String columnType = parser.text();

                            if (!"NULL".equals(columnType)) {
                                parser.nextToken();
                                String valueField = parser.currentName();
                                if (!"value".equals(valueField)) {
                                    throw new IllegalArgumentException("wrong column value, expect value field but got " + valueField);
                                }
                                parser.nextToken();
                            }

                            switch (columnType) {
                                case "NULL":
                                    values.add(new NullValue());
                                    break;
                                case "BOOLEAN":
                                    values.add(new BooleanValue(parser.booleanValue()));
                                    break;
                                case "STRING":
                                    values.add(new StringValue(parser.text()));
                                    break;
                                case "SHORT":
                                    values.add(new ShortValue(parser.shortValue()));
                                    break;
                                case "INTEGER":
                                    values.add(new IntValue(parser.intValue()));
                                    break;
                                case "LONG":
                                    values.add(new LongValue(parser.longValue()));
                                    break;
                                case "FLOAT":
                                    values.add(new FloatValue(parser.floatValue()));
                                    break;
                                case "DOUBLE":
                                    values.add(new DoubleValue(parser.doubleValue()));
                                    break;
                                default:
                                    break;
                            }
                            parser.skipChildren();
                            parser.nextToken();
                        }
                    }
                    break;
                default:
                    parser.skipChildren();
                    break;
            }
        }
        return new Row(values.toArray(new ColumnValue[0]));
    }

    public XContentBuilder toXContent(XContentBuilder builder) throws IOException {
        return toXContent(builder, EMPTY_PARAMS);
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        builder.startArray("values");
        for (ColumnValue value : values) {
            value.toXContent(builder, params);
        }
        builder.endArray();
        builder.endObject();
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Row other = (Row) o;
        if (this.size() != other.size()) {
            return false;
        }
        for (int i = 0; i < this.size(); i++) {
            if (!this.getValue(i).equals(other.getValue(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean equals(Row other) {
        if (this.size() != other.size()) {
            return false;
        }
        for (int i = 0; i < this.size(); i++) {
            if (!this.getValue(i).equals(other.getValue(i))) {
                return false;
            }
        }
        return true;
    }
}
