/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class MLIndexInsightTypeTests {
    @Test
    public void testValidTypes() {
        assertEquals(MLIndexInsightType.STATISTICAL_DATA, MLIndexInsightType.fromString("STATISTICAL_DATA"));
        assertEquals(MLIndexInsightType.FIELD_DESCRIPTION, MLIndexInsightType.fromString("FIELD_DESCRIPTION"));
        assertEquals(MLIndexInsightType.LOG_RELATED_INDEX_CHECK, MLIndexInsightType.fromString("LOG_RELATED_INDEX_CHECK"));
        assertEquals(MLIndexInsightType.ALL, MLIndexInsightType.fromString("ALL"));
    }

    @Test
    public void testNullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> MLIndexInsightType.fromString(null));
        assertEquals("ML index insight type can't be null", exception.getMessage());
    }

    @Test
    public void testInvalidType() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> MLIndexInsightType.fromString("INVALID_TYPE")
        );
        assertEquals("Wrong index insight type", exception.getMessage());
    }
}
