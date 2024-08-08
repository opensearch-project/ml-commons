/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import java.math.BigDecimal;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ColumnTypeTest {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void from_WrongType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("unsupported type:java.math.BigDecimal");
        BigDecimal obj = new BigDecimal("0");
        ColumnType type = ColumnType.from(obj);
    }
}
