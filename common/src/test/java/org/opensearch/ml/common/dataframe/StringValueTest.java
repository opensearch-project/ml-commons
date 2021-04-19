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

    @Test(expected = NullPointerException.class)
    public void stringValue_NullPointerException() {
        new StringValue(null);
    }
}