/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.opensearch.ml.common.TestHelper.testParse;
import static org.opensearch.ml.common.TestHelper.testParseFromString;

import java.io.IOException;
import java.util.Iterator;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.MediaTypeRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.core.xcontent.XContentParser;

public class RowTest {
    Row row;

    Function<XContentParser, Row> function;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() {
        row = new Row(1);
        row.setValue(0, ColumnValueBuilder.build(0));

        function = parser -> {
            try {
                return Row.parse(parser);
            } catch (IOException e) {
                throw new RuntimeException("failed to parse", e);
            }
        };
    }

    @Test
    public void setAndGetValue() {
        row.setValue(0, ColumnValueBuilder.build(1));
        assertEquals(1, row.getValue(0).intValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue_Exception_IndexLessThanZero() {
        row.setValue(-1, ColumnValueBuilder.build(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void setValue_Exception_IndexBiggerThanSize() {
        row.setValue(1, ColumnValueBuilder.build(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getValue_Exception_IndexLessThanZero() {
        row.getValue(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getValue_Exception_IndexBiggerThanSize() {
        row.getValue(row.size());
    }

    @Test
    public void iterator() {
        Iterator<ColumnValue> iterator = row.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(0, iterator.next().intValue());
    }

    @Test
    public void size() {
        assertEquals(1, row.size());
    }

    @Test
    public void writeToAndReadStream() throws IOException {
        BytesStreamOutput bytesStreamOutput = new BytesStreamOutput();
        row.writeTo(bytesStreamOutput);
        row = new Row(bytesStreamOutput.bytes().streamInput());
        assertEquals(1, row.size());
    }

    @Test
    public void remove() {
        row = new Row(2);
        row.setValue(0, ColumnValueBuilder.build(0));
        row.setValue(1, ColumnValueBuilder.build(false));
        row = row.remove(1);
        assertEquals(1, row.size());
        assertEquals(0, row.getValue(0).intValue());
    }

    @Test
    public void remove_WrongIndex() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("removed index can't be negative or bigger than row's values length:0");
        row = new Row(0);
        row.remove(0);
    }

    @Test
    public void remove_WrongNegativeIndex() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("removed index can't be negative or bigger than row's values length:0");
        row = new Row(0);
        row.remove(-1);
    }

    @Test
    public void select() {
        row = new Row(2);
        row.setValue(0, ColumnValueBuilder.build(0));
        row.setValue(1, ColumnValueBuilder.build(false));
        row = row.select(new int[] { 1 });
        assertEquals(1, row.size());
        assertFalse(row.getValue(0).booleanValue());
    }

    @Test
    public void testToXContent() throws IOException {
        XContentBuilder builder = MediaTypeRegistry.contentBuilder(XContentType.JSON);
        row.toXContent(builder);

        assertNotNull(builder);
        String jsonStr = builder.toString();

        assertEquals("{\"values\":[{\"column_type\":\"INTEGER\",\"value\":0}]}", jsonStr);
    }

    @Test
    public void testParse_NullValue() throws IOException {
        ColumnValue[] values = new ColumnValue[] { new NullValue() };
        Row row = new Row(values);
        testParse(row, function);
    }

    @Test
    public void testParse_NullValue_AtLast() throws IOException {
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new StringValue("test"),
            new BooleanValue(true),
            new NullValue() };
        Row row = new Row(values);
        testParse(row, function);
    }

    @Test
    public void testParse_NullValue_AtFirst() throws IOException {
        ColumnValue[] values = new ColumnValue[] {
            new NullValue(),
            new IntValue(1),
            new DoubleValue(2.0),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        testParse(row, function);
    }

    @Test
    public void testParse_NullValue_AtMiddle() throws IOException {
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        testParse(row, function);
    }

    @Test
    public void testParse_ExtraWrongValueField() throws IOException {
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        String jsonStr = "{\"values\":[{\"column_type\":\"INTEGER\",\"value\":1},{\"column_type\":\"DOUBLE\",\"value\":2},"
            + "{\"column_type\":\"NULL\"},{\"column_type\":\"STRING\",\"value\":\"test\"},{\"column_type\":\"BOOLEAN\","
            + "\"value\":true},{\"column_type\":\"WRONG\",\"value\":true}],\"wrong_filed\":{\"test\":\"abc\"}}";
        testParseFromString(row, jsonStr, function);
    }

    @Test
    public void testParse_EmptyValueField() throws IOException {
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        String jsonStr = "{\"values\":[{}]}";
        testParseFromString(row, jsonStr, function);
    }

    @Test
    public void testParse_WrongColumnTypeField() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong column type, expect column_type field but got column_type_wrong");
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        String jsonStr = "{\"values\":[{\"column_type_wrong\":\"INTEGER\",\"value\":1}]}";
        testParseFromString(row, jsonStr, function);
    }

    @Test
    public void testParse_WrongValueField() throws IOException {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("wrong column value, expect value field but got value_wrong");
        ColumnValue[] values = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row = new Row(values);
        String jsonStr = "{\"values\":[{\"column_type\":\"INTEGER\",\"value_wrong\":1}]}";
        testParseFromString(row, jsonStr, function);
    }

    @Test
    public void testEquals_EmptyValues() {
        ColumnValue[] values1 = new ColumnValue[] {};
        Row row1 = new Row(values1);
        ColumnValue[] values2 = new ColumnValue[] {};
        Row row2 = new Row(values2);
        assertTrue(row1.equals(row2));

        ColumnValue[] values3 = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row3 = new Row(values3);
        assertFalse(row1.equals(row3));
    }

    @Test
    public void testEquals_AllValuesMatch() {
        ColumnValue[] values1 = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row1 = new Row(values1);
        ColumnValue[] values2 = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row2 = new Row(values2);
        assertTrue(row1.equals(row2));
    }

    @Test
    public void testEquals_SomeValueNotMatch() {
        ColumnValue[] values1 = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row1 = new Row(values1);
        ColumnValue[] values2 = new ColumnValue[] {
            new IntValue(2),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row2 = new Row(values2);
        assertFalse(row1.equals(row2));
    }

    @Test
    public void testEquals_SomeTypeNotMatch() {
        ColumnValue[] values1 = new ColumnValue[] {
            new IntValue(1),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row1 = new Row(values1);
        ColumnValue[] values2 = new ColumnValue[] {
            new DoubleValue(1.0),
            new DoubleValue(2.0),
            new NullValue(),
            new StringValue("test"),
            new BooleanValue(true) };
        Row row2 = new Row(values2);
        assertFalse(row1.equals(row2));
    }

}
