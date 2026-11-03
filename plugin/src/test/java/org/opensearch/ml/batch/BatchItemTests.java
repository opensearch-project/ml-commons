/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.batch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class BatchItemTests {

    @Test
    public void constructor_KeepsPayloadAndByteSize() {
        BatchItem item = new BatchItem("hello", 5L);
        assertEquals("hello", item.getPayload());
        assertEquals(5L, item.getByteSize());
    }

    @Test
    public void constructor_AcceptsZeroByteSize() {
        BatchItem item = new BatchItem("", 0L);
        assertEquals("", item.getPayload());
        assertEquals(0L, item.getByteSize());
    }

    /** A null doc is sized as 0 by the text-docs handler rather than being dropped. */
    @Test
    public void constructor_AcceptsNullPayload() {
        BatchItem item = new BatchItem(null, 0L);
        assertNull(item.getPayload());
        assertEquals(0L, item.getByteSize());
    }

    @Test
    public void constructor_RejectsNegativeByteSize() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new BatchItem("hello", -1L));
        assertEquals("byteSize must be non-negative, but got -1", e.getMessage());
    }
}
