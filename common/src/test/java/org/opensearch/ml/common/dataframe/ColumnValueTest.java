/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 *
 */

package org.opensearch.ml.common.dataframe;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.junit.Assert.assertTrue;

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
        ColumnValue value = new IntValue(1);
        value.doubleValue();
    }

    @Test
    public void wrongFloatValue() {
        exceptionRule.expect(RuntimeException.class);
        exceptionRule.expectMessage("the value isn't Float type");
        ColumnValue value = new IntValue(1);
        value.floatValue();
    }
}
