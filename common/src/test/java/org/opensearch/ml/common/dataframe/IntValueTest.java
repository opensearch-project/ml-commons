package org.opensearch.ml.common.dataframe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IntValueTest {

    @Test
    public void intValue() {
        IntValue intValue = new IntValue(2);
        assertEquals(ColumnType.INTEGER, intValue.columnType());
        assertEquals(2, intValue.getValue());
        assertEquals(2, intValue.intValue());
    }
}