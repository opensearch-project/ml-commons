package org.opensearch.ml.common.dataframe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StringValueTest {
    @Test
    public void stringValue() {
        StringValue value = new StringValue("str");
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("str", value.stringValue());
        assertEquals("str", value.getValue());
    }
}