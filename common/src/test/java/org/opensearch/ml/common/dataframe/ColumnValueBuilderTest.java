package org.opensearch.ml.common.dataframe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColumnValueBuilderTest {

    @Test
    public void build() {
        ColumnValue value = ColumnValueBuilder.build(null);
        assertEquals(ColumnType.NULL, value.columnType());

        value = ColumnValueBuilder.build(2);
        assertEquals(ColumnType.INTEGER, value.columnType());
        assertEquals(2, value.intValue());

        value = ColumnValueBuilder.build("string");
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("string", value.stringValue());

        value = ColumnValueBuilder.build(2.1D);
        assertEquals(ColumnType.DOUBLE, value.columnType());
        assertEquals(2.1D, value.doubleValue(), 0.001D);

        value = ColumnValueBuilder.build(true);
        assertEquals(ColumnType.BOOLEAN, value.columnType());
        assertEquals(true, value.booleanValue());
    }
}