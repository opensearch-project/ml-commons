/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ColumnValueTest {
    @Test
    public void equals_SameObject() {
        ColumnValue value = new IntValue(1);
        assertTrue(value.equals(value));
    }

    @Test
    public void wrongIntValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new DoubleValue(1.0);
            value.intValue();
        });
        assertEquals("the value isn't Integer type", exception.getMessage());
    }

    @Test
    public void wrongBooleanValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new DoubleValue(1.0);
            value.booleanValue();
        });
        assertEquals("the value isn't Boolean type", exception.getMessage());
    }

    @Test
    public void wrongStringValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new DoubleValue(1.0);
            value.stringValue();
        });
        assertEquals("the value isn't String type", exception.getMessage());
    }

    @Test
    public void wrongDoubleValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new BooleanValue(true);
            value.doubleValue();
        });
        assertEquals("the value isn't Double type", exception.getMessage());
    }

    @Test
    public void wrongFloatValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new IntValue(1);
            value.floatValue();
        });
        assertEquals("the value isn't Float type", exception.getMessage());
    }

    @Test
    public void wrongShortValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new IntValue(1);
            value.shortValue();
        });
        assertEquals("the value isn't Short type", exception.getMessage());
    }

    @Test
    public void wrongLongValue() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            ColumnValue value = new IntValue(1);
            value.longValue();
        });
        assertEquals("the value isn't Long type", exception.getMessage());
    }
}
