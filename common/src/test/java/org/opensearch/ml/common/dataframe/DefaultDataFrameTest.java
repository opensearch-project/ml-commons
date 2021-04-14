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
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamInput;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DefaultDataFrameTest {

    DefaultDataFrame defaultDataFrame;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        ColumnMeta[] columnMetas = new ColumnMeta[4];
        columnMetas[0] = ColumnMeta.builder()
                .name("c1")
                .columnType(ColumnType.STRING)
                .build();
        columnMetas[1] = ColumnMeta.builder()
                .name("c2")
                .columnType(ColumnType.INTEGER)
                .build();
        columnMetas[2] = ColumnMeta.builder()
                .name("c3")
                .columnType(ColumnType.DOUBLE)
                .build();

        columnMetas[3] = ColumnMeta.builder()
                .name("c4")
                .columnType(ColumnType.BOOLEAN)
                .build();

        Row row = new Row(4);
        row.setValue(0, new StringValue("string"));
        row.setValue(1, new IntValue(1));
        row.setValue(2, new DoubleValue(2.0D));
        row.setValue(3, new BooleanValue(true));
        List<Row> rows = new ArrayList<>();
        rows.add(row);
        defaultDataFrame = new DefaultDataFrame(columnMetas, rows);
    }

    @Test
    public void writeTo_Success_SimpleDataFrame() throws IOException {
        assertEquals(1, defaultDataFrame.size());
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        defaultDataFrame.writeTo(bytesStreamOutput);
        defaultDataFrame = new DefaultDataFrame(bytesStreamOutput.bytes().streamInput(), true);
        assertEquals(1, defaultDataFrame.size());
        assertEquals(4, defaultDataFrame.iterator().next().size());
    }

    @Test
    public void readInputStream_Success() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        defaultDataFrame.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        assertEquals(DataFrameType.DEFAULT, streamInput.readEnum(DataFrameType.class));
        defaultDataFrame = new DefaultDataFrame(streamInput);
        assertEquals(1, defaultDataFrame.size());
        assertEquals(4, defaultDataFrame.iterator().next().size());
    }

    @Test
    public void appendRow_Success() {
        Row row = new Row(4);
        row.setValue(0, new StringValue("string2"));
        row.setValue(1, new IntValue(2));
        row.setValue(2, new DoubleValue(3.0D));
        row.setValue(3, new BooleanValue(true));
        int size = defaultDataFrame.size();
        defaultDataFrame.appendRow(row);
        assertEquals(size + 1, defaultDataFrame.size());
    }

    @Test
    public void appendRow_Exception_NullRow() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input row can't be null");
        Row row = null;
        defaultDataFrame.appendRow(row);
    }

    @Test
    public void appendRow_Success_ObjectArray() {
        Object[] values = new Object[4];
        values[0] = "string2";
        values[1] = 2;
        values[2] = 3.0D;
        values[3] = true;

        int size = defaultDataFrame.size();
        defaultDataFrame.appendRow(values);
        assertEquals(size + 1, defaultDataFrame.size());
        Row row = defaultDataFrame.getRow(size);
        assertEquals("string2", row.getValue(0).stringValue());
        assertEquals(2, row.getValue(1).intValue());
        assertEquals(3.0D, row.getValue(2).doubleValue(), 0.0001d);
        assertTrue(row.getValue(3).booleanValue());
    }

    @Test
    public void appendRow_Exception_NullValues() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("input values can't be null");
        Object[] values = null;
        defaultDataFrame.appendRow(values);
    }

    @Test
    public void appendRow_Exception_DifferentColumns() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("the size is different between input row:3 and column size in dataframe:4");
        Row row = new Row(3);
        row.setValue(0, new StringValue("string2"));
        row.setValue(1, new IntValue(2));
        row.setValue(2, new DoubleValue(3.0D));
        defaultDataFrame.appendRow(row);
    }

    @Test
    public void appendRow_Exception_DifferentColumnTypes() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("the column type is different in column meta:BOOLEAN and input row:DOUBLE for " +
                "index: 3");
        Row row = new Row(4);
        row.setValue(0, new StringValue("string2"));
        row.setValue(1, new IntValue(2));
        row.setValue(2, new DoubleValue(3.0D));
        row.setValue(3, new DoubleValue(4.0D));
        defaultDataFrame.appendRow(row);
    }

    @Test
    public void columnMetas_Success() {
        ColumnMeta[] metas = defaultDataFrame.columnMetas();
        assertEquals(4, metas.length);
    }
}