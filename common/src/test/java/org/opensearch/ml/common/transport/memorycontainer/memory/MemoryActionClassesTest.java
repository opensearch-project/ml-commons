/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Tests for memory-related Action classes
 */
public class MemoryActionClassesTest {

    @Test
    public void testMLAddMemoriesAction() {
        assertNotNull(MLAddMemoriesAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/memories/add", MLAddMemoriesAction.NAME);
        assertEquals(MLAddMemoriesAction.NAME, MLAddMemoriesAction.INSTANCE.name());
    }

    @Test
    public void testMLSearchMemoriesAction() {
        assertNotNull(MLSearchMemoriesAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/memories/search", MLSearchMemoriesAction.NAME);
        assertEquals(MLSearchMemoriesAction.NAME, MLSearchMemoriesAction.INSTANCE.name());
    }

    @Test
    public void testMLDeleteMemoryAction() {
        assertNotNull(MLDeleteMemoryAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/memory/delete", MLDeleteMemoryAction.NAME);
        assertEquals(MLDeleteMemoryAction.NAME, MLDeleteMemoryAction.INSTANCE.name());
    }

    @Test
    public void testMLUpdateMemoryAction() {
        assertNotNull(MLUpdateMemoryAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/memory/update", MLUpdateMemoryAction.NAME);
        assertEquals(MLUpdateMemoryAction.NAME, MLUpdateMemoryAction.INSTANCE.name());
    }
}
