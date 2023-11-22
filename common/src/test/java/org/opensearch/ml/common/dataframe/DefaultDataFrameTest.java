/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.ml.common.TestHelper;

public class DefaultDataFrameTest {

    DefaultDataFrame defaultDataFrame;
    Function<XContentParser, DefaultDataFrame> function;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setUp() {
        ColumnMeta[] columnMetas = new ColumnMeta[4];
        columnMetas[0] = ColumnMeta.builder().name("c1").columnType(ColumnType.STRING).build();
        columnMetas[1] = ColumnMeta.builder().name("c2").columnType(ColumnType.INTEGER).build();
        columnMetas[2] = ColumnMeta.builder().name("c3").columnType(ColumnType.DOUBLE).build();

        columnMetas[3] = ColumnMeta.builder().name("c4").columnType(ColumnType.BOOLEAN).build();

        Row row = new Row(4);
        row.setValue(0, new StringValue("string"));
        row.setValue(1, new IntValue(1));
        row.setValue(2, new DoubleValue(2.0D));
        row.setValue(3, new BooleanValue(true));
        List<Row> rows = new ArrayList<>();
        rows.add(row);
        defaultDataFrame = new DefaultDataFrame(columnMetas, rows);

        function = parser -> {
            try {
                return DefaultDataFrame.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse DefaultDataFrame", e);
            }
        };
    }

    @Test
    public void writeTo_Success_SimpleDataFrame() throws IOException {
        assertEquals(1, defaultDataFrame.size());
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        defaultDataFrame.writeTo(bytesStreamOutput);
        StreamInput streamInput = bytesStreamOutput.bytes().streamInput();
        assertEquals(DataFrameType.DEFAULT, streamInput.readEnum(DataFrameType.class));
        defaultDataFrame = new DefaultDataFrame(streamInput);
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
        assertEquals(DataFrameType.DEFAULT, defaultDataFrame.getDataFrameType());
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
        exceptionRule.expectMessage("the column type is different in column meta:BOOLEAN and input row:DOUBLE for " + "index: 3");
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

    @Test
    public void remove_Exception_InputColumnIndexBiggerThanColumensLength() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("columnIndex can't be negative or bigger than columns length:4");
        defaultDataFrame.remove(4);
    }

    @Test
    public void remove_Exception_InputColumnIndexNegtiveColumensLength() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("columnIndex can't be negative or bigger than columns length:4");
        defaultDataFrame.remove(-1);
    }

    @Test
    public void remove_EmptyColumnMeta() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("columnIndex can't be negative or bigger than columns length:0");
        DefaultDataFrame dataFrame = new DefaultDataFrame(new ColumnMeta[0]);
        DataFrame newDataFrame = dataFrame.remove(0);
        assertEquals(0, newDataFrame.size());
    }

    @Test
    public void remove_Success() {
        DataFrame dataFrame = defaultDataFrame.remove(3);
        assertEquals(3, dataFrame.columnMetas().length);
        assertEquals(3, dataFrame.getRow(0).size());
    }

    @Test
    public void select_Success() {
        DataFrame dataFrame = defaultDataFrame.select(new int[] { 1, 3 });
        assertEquals(2, dataFrame.columnMetas().length);
        assertEquals(2, dataFrame.getRow(0).size());
    }

    @Test
    public void select_Exception_EmptyInputColumns() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("columns can't be null or empty");
        defaultDataFrame.select(new int[0]);
    }

    @Test
    public void select_Exception_InvalidColumn() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("columnIndex can't be negative or bigger than columns length");
        defaultDataFrame.select(new int[] { 5 });
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        builder.startObject();
        defaultDataFrame.toXContent(builder);
        builder.endObject();

        assertNotNull(builder);
        String jsonStr = builder.toString();
        assertEquals(
            "{\"column_metas\":["
                + "{\"name\":\"c1\",\"column_type\":\"STRING\"},"
                + "{\"name\":\"c2\",\"column_type\":\"INTEGER\"},"
                + "{\"name\":\"c3\",\"column_type\":\"DOUBLE\"},"
                + "{\"name\":\"c4\",\"column_type\":\"BOOLEAN\"}],"
                + "\"rows\":["
                + "{\"values\":["
                + "{\"column_type\":\"STRING\",\"value\":\"string\"},"
                + "{\"column_type\":\"INTEGER\",\"value\":1},"
                + "{\"column_type\":\"DOUBLE\",\"value\":2.0},"
                + "{\"column_type\":\"BOOLEAN\",\"value\":true}]}]}",
            jsonStr
        );
    }

    @Test
    public void testParse_EmptyDataFrame() throws IOException {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("test_int", ColumnType.INTEGER) };
        DefaultDataFrame dataFrame = new DefaultDataFrame(columnMetas);
        TestHelper.testParse(dataFrame, function, true);
    }

    @Test
    public void testParse_DataFrame() throws IOException {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("test_int", ColumnType.INTEGER) };
        DefaultDataFrame dataFrame = new DefaultDataFrame(columnMetas);
        dataFrame.appendRow(new Integer[] { 1 });
        dataFrame.appendRow(new Integer[] { 2 });
        TestHelper.testParse(dataFrame, function, true);
    }

    @Test
    public void testParse_WrongExtraField() throws IOException {
        ColumnMeta[] columnMetas = new ColumnMeta[] { new ColumnMeta("test_int", ColumnType.INTEGER) };
        DefaultDataFrame dataFrame = new DefaultDataFrame(columnMetas);
        dataFrame.appendRow(new Integer[] { 1 });
        dataFrame.appendRow(new Integer[] { 2 });
        String jsonStr = "{\"wrong_field\":{\"test\":\"abc\"},\"column_metas\":[{\"name\":\"test_int\",\"column_type\":"
            + "\"INTEGER\"}],\"rows\":[{\"values\":[{\"column_type\":\"INTEGER\",\"value\":1}]},{\"values\":"
            + "[{\"column_type\":\"INTEGER\",\"value\":2}]}]}";
        TestHelper.testParseFromString(dataFrame, jsonStr, function);
    }
}
