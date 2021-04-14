package org.opensearch.ml.common.dataframe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NullValueTest {

    @Test
    public void getValue() {
        NullValue value = new NullValue();
        assertNull(value.getValue());
        assertEquals(ColumnType.NULL, value.columnType());
    }
}