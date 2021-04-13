package org.opensearch.ml.common.dataframe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BooleanValueTest {
    @Test
    public void booleanValue() {
        BooleanValue value = new BooleanValue(true);
        assertEquals(ColumnType.BOOLEAN, value.columnType());
        assertTrue(value.booleanValue());
        assertEquals(true, value.getValue());
    }

}