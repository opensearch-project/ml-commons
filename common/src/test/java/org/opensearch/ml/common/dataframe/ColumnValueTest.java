/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ColumnValueTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void equals_SameObject() {
        ColumnValue value = new IntValue(1);
        assertTrue(value.equals(value));
    }

    @Test
    public void wrongIntValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Integer type");
        ColumnValue value = new DoubleValue(1.0);
        value.intValue();
    }

    @Test
    public void wrongBooleanValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Boolean type");
        ColumnValue value = new DoubleValue(1.0);
        value.booleanValue();
    }

    @Test
    public void wrongStringValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't String type");
        ColumnValue value = new DoubleValue(1.0);
        value.stringValue();
    }

    @Test
    public void wrongDoubleValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Double type");
        ColumnValue value = new BooleanValue(true);
        value.doubleValue();
    }

    @Test
    public void wrongFloatValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Float type");
        ColumnValue value = new IntValue(1);
        value.floatValue();
    }

    @Test
    public void wrongShortValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Short type");
        ColumnValue value = new IntValue(1);
        value.shortValue();
    }

    @Test
    public void wrongLongValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Long type");
        ColumnValue value = new IntValue(1);
        value.longValue();
    }
}
