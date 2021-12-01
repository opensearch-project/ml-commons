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

import java.math.BigDecimal;

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
