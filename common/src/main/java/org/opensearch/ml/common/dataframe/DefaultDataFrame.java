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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.StreamOutput;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@ToString
public class DefaultDataFrame extends AbstractDataFrame{
    List<Row> rows;
    ColumnMeta[] columnMetas;

    public DefaultDataFrame(final ColumnMeta[] columnMetas){
        super(DataFrameType.DEFAULT);
        this.columnMetas = columnMetas;
        this.rows = new ArrayList<>();
    }

    public DefaultDataFrame(final ColumnMeta[] columnMetas, final List<Row> rows){
        super(DataFrameType.DEFAULT);
        this.columnMetas = columnMetas;
        this.rows = rows;
    }

    public DefaultDataFrame(StreamInput streamInput) throws IOException {
        this(streamInput, false);
    }

    public DefaultDataFrame(StreamInput streamInput, boolean readType) throws IOException {
        super(readType ? streamInput.readEnum(DataFrameType.class) : DataFrameType.DEFAULT);
        this.columnMetas = streamInput.readArray(ColumnMeta::new, ColumnMeta[]::new);
        this.rows = streamInput.readList(Row::new);
    }

    @Override
    public void appendRow(final Object[] values) {
        if(values == null) {
            throw new IllegalArgumentException("input values can't be null");
        }

        Row row = new Row(values.length);
        for(int i = 0; i < values.length; i++) {
            row.setValue(i, ColumnValueBuilder.build(values[i]));
        }

        appendRow(row);
    }

    @Override
    public void appendRow(final Row row) {
        if(row == null) {
            throw new IllegalArgumentException("input row can't be null");
        }

        if(row.size() != columnMetas.length) {
            final String message = String.format("the size is different between input row:%d " +
                    "and column size in dataframe:%d", row.size(), columnMetas.length);
            throw new IllegalArgumentException(message);
        }

        for(int i = 0; i < columnMetas.length; i++) {
            if(columnMetas[i].getColumnType() != row.getValue(i).columnType()) {
                final String message = String.format("the column type is different in column meta:%s and input row:%s for index: %d",
                        columnMetas[i].getColumnType(), row.getValue(i).columnType(), i);
                throw new IllegalArgumentException(message);
            }
        }

        this.rows.add(row);
    }

    public Row getRow(int index) {
        return rows.get(index);
    }

    @Override
    public int size() {
        return this.rows.size();
    }

    @Override
    public ColumnMeta[] columnMetas() {
        return Arrays.copyOf(columnMetas, columnMetas.length);
    }

    @Override
    public Iterator<Row> iterator() {
        return rows.iterator();
    }


    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeArray(columnMetas);
        out.writeList(rows);
    }
}
