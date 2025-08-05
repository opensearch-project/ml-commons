/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.common.transport.memorycontainer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Tests for memory container Action classes
 */
public class MemoryContainerActionClassesTest {

    @Test
    public void testMLCreateMemoryContainerAction() {
        assertNotNull(MLCreateMemoryContainerAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/create", MLCreateMemoryContainerAction.NAME);
        assertEquals(MLCreateMemoryContainerAction.NAME, MLCreateMemoryContainerAction.INSTANCE.name());
    }

    @Test
    public void testMLMemoryContainerGetAction() {
        assertNotNull(MLMemoryContainerGetAction.INSTANCE);
        assertEquals("cluster:admin/opensearch/ml/memory_containers/get", MLMemoryContainerGetAction.NAME);
        assertEquals(MLMemoryContainerGetAction.NAME, MLMemoryContainerGetAction.INSTANCE.name());
    }
}
