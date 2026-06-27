/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class IndexInsightTaskStatusTests {
    @Test
    public void testValidStatuses() {
        assertEquals(IndexInsightTaskStatus.GENERATING, IndexInsightTaskStatus.fromString("GENERATING"));
        assertEquals(IndexInsightTaskStatus.COMPLETED, IndexInsightTaskStatus.fromString("COMPLETED"));
        assertEquals(IndexInsightTaskStatus.FAILED, IndexInsightTaskStatus.fromString("FAILED"));
    }

    @Test
    public void testNullInput() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> IndexInsightTaskStatus.fromString(null));
        assertEquals("Index insight task status can't be null", exception.getMessage());
    }

    @Test
    public void testInvalidStatus() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> IndexInsightTaskStatus.fromString("INVALID_STATUS")
        );
        assertEquals("Wrong index insight task status", exception.getMessage());
    }
}
