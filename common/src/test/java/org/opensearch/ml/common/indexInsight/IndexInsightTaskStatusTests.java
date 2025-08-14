/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.indexInsight;

import static org.junit.Assert.assertEquals;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class IndexInsightTaskStatusTests {

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void testValidStatuses() {
        assertEquals(IndexInsightTaskStatus.GENERATING, IndexInsightTaskStatus.fromString("GENERATING"));
        assertEquals(IndexInsightTaskStatus.COMPLETED, IndexInsightTaskStatus.fromString("COMPLETED"));
        assertEquals(IndexInsightTaskStatus.FAILED, IndexInsightTaskStatus.fromString("FAILED"));

        assertEquals(IndexInsightTaskStatus.GENERATING, IndexInsightTaskStatus.fromString("generating"));
        assertEquals(IndexInsightTaskStatus.COMPLETED, IndexInsightTaskStatus.fromString("completed"));
        assertEquals(IndexInsightTaskStatus.FAILED, IndexInsightTaskStatus.fromString("failed"));
    }

    @Test
    public void testNullInput() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Index insight task status can't be null");
        IndexInsightTaskStatus.fromString(null);
    }

    @Test
    public void testInvalidStatus() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Wrong index insight task status");
        IndexInsightTaskStatus.fromString("INVALID_STATUS");
    }
}
