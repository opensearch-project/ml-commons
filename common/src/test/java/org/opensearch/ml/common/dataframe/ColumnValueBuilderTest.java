/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;

import java.math.BigDecimal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ColumnValueBuilderTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void build() {
        ColumnValue value = ColumnValueBuilder.build(null);
        assertEquals(ColumnType.NULL, value.columnType());

        value = ColumnValueBuilder.build(2);
        assertEquals(ColumnType.INTEGER, value.columnType());
        assertEquals(2, value.intValue());
        assertEquals(2.0d, value.doubleValue(), 1e-5);

        value = ColumnValueBuilder.build("string");
        assertEquals(ColumnType.STRING, value.columnType());
        assertEquals("string", value.stringValue());

        value = ColumnValueBuilder.build(2.1D);
        assertEquals(ColumnType.DOUBLE, value.columnType());
        assertEquals(2.1D, value.doubleValue(), 0.001D);

        value = ColumnValueBuilder.build(true);
        assertEquals(ColumnType.BOOLEAN, value.columnType());
        assertEquals(true, value.booleanValue());

        value = ColumnValueBuilder.build(2.1f);
        assertEquals(ColumnType.FLOAT, value.columnType());
        assertEquals(2.1f, value.floatValue(), 1e-5);
        assertEquals(2.1d, value.doubleValue(), 1e-5);

        value = ColumnValueBuilder.build((short) 2);
        assertEquals(ColumnType.SHORT, value.columnType());
        assertEquals(2, value.shortValue());
        assertEquals(2.0d, value.doubleValue(), 1e-5);

        value = ColumnValueBuilder.build((long) 2);
        assertEquals(ColumnType.LONG, value.columnType());
        assertEquals(2, value.longValue());
        assertEquals(2.0d, value.doubleValue(), 1e-5);
    }

    @Test
    public void build_IllegalType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported type:java.math.BigDecimal");
        Object obj = new BigDecimal("0");
        ColumnValueBuilder.build(obj);
    }
}
