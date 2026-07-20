/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.dataframe;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;

import org.junit.Test;

public class ColumnTypeTest {
    @Test
    public void from_WrongType() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            BigDecimal obj = new BigDecimal("0");
            ColumnType type = ColumnType.from(obj);
        });
        assertEquals("unsupported type:java.math.BigDecimal", exception.getMessage());
    }
}
