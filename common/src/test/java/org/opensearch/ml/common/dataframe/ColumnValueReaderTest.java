/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.opensearch.common.io.stream.BytesStreamOutput;

public class ColumnValueReaderTest {
    ColumnValueReader reader = new ColumnValueReader();

    @Test
    public void read_NullValue() throws IOException {
        ColumnValue value = new NullValue();
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.NULL, value.columnType());
    }

    @Test
    public void read_IntValue() throws IOException {
        ColumnValue value = new IntValue(2);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.INTEGER, value.columnType());
        assertEquals(2, value.intValue());
    }

    @Test
    public void read_StringValue() throws IOException {
        ColumnValue value = new StringValue("string");
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("string", value.stringValue());
    }

    @Test
    public void read_StringValue_Empty() throws IOException {
        ColumnValue value = new StringValue("");
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("", value.stringValue());
    }

    @Test
    public void read_DoubleValue() throws IOException {
        ColumnValue value = new DoubleValue(2.1D);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.DOUBLE, value.columnType());
        assertEquals(2.1D, value.doubleValue(), 0.00001D);
    }

    @Test
    public void read_BooleanValue() throws IOException {
        ColumnValue value = new BooleanValue(true);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.BOOLEAN, value.columnType());
        assertEquals(true, value.booleanValue());
    }

    @Test
    public void read_FloatValue() throws IOException {
        ColumnValue value = new FloatValue(2.1f);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.FLOAT, value.columnType());
        assertEquals(2.1f, value.floatValue(), 1e-5);
    }

    @Test
    public void read_ShortValue() throws IOException {
        ColumnValue value = new ShortValue((short) 2);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.SHORT, value.columnType());
        assertEquals(2, value.shortValue());
    }

    @Test
    public void read_LongValue() throws IOException {
        ColumnValue value = new LongValue((long) 2);
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        value.writeTo(bytesStreamOutput);
        value = reader.read(bytesStreamOutput.bytes().streamInput());
        assertEquals(ColumnType.LONG, value.columnType());
        assertEquals(2, value.longValue());
    }
}
