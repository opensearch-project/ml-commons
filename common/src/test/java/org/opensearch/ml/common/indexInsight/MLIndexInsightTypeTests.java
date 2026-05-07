/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MLIndexInsightTypeTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testValidTypes() {
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, MLIndexInsightType.fromString("STATISTICAL_DATA"));
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, MLIndexInsightType.fromString("FIELD_DESCRIPTION"));
        assertEquals(MLIndexInsightType.LOG_RELATED_INDEX_CHECK, MLIndexInsightType.fromString("LOG_RELATED_INDEX_CHECK"));
        assertEquals(MLIndexInsightType.INDEX_CORRELATION, MLIndexInsightType.fromString("INDEX_CORRELATION"));
        assertEquals(MLIndexInsightType.ALL, MLIndexInsightType.fromString("ALL"));
    }

    @Test
    public void testNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("ML index insight type can't be null");
        MLIndexInsightType.fromString(null);
    }

    @Test
    public void testInvalidType() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong index insight type");
        MLIndexInsightType.fromString("INVALID_TYPE");
    }
}
