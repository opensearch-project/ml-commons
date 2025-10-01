/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.action.memorycontainer.memory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opensearch.ml.common.memorycontainer.MemoryType;
import org.opensearch.test.OpenSearchTestCase;

public class MemoryInfoTests extends OpenSearchTestCase {

    @Test
    public void testConstructor() {
        String memoryId = "memory-123";
        String content = "Test memory content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(memoryId, content, type, includeInResponse);

        assertNotNull(memoryInfo);
        assertEquals(memoryId, memoryInfo.getMemoryId());
        assertEquals(content, memoryInfo.getContent());
        assertEquals(type, memoryInfo.getType());
        assertTrue(memoryInfo.isIncludeInResponse());
    }

    @Test
    public void testConstructorWithNullMemoryId() {
        String content = "Test memory content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(null, content, type, includeInResponse);

        assertNull(memoryInfo.getMemoryId());
        assertEquals(content, memoryInfo.getContent());
        assertEquals(type, memoryInfo.getType());
        assertTrue(memoryInfo.isIncludeInResponse());
    }

    @Test
    public void testConstructorWithIncludeInResponseFalse() {
        String memoryId = "memory-123";
        String content = "Test memory content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = false;

        MemoryInfo memoryInfo = new MemoryInfo(memoryId, content, type, includeInResponse);

        assertEquals(memoryId, memoryInfo.getMemoryId());
        assertEquals(content, memoryInfo.getContent());
        assertEquals(type, memoryInfo.getType());
        assertFalse(memoryInfo.isIncludeInResponse());
    }

    @Test
    public void testSetMemoryId() {
        String initialId = "memory-123";
        String newId = "memory-456";
        String content = "Test memory content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(initialId, content, type, includeInResponse);
        assertEquals(initialId, memoryInfo.getMemoryId());

        memoryInfo.setMemoryId(newId);
        assertEquals(newId, memoryInfo.getMemoryId());
    }

    @Test
    public void testSetMemoryIdToNull() {
        String initialId = "memory-123";
        String content = "Test memory content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(initialId, content, type, includeInResponse);
        assertEquals(initialId, memoryInfo.getMemoryId());

        memoryInfo.setMemoryId(null);
        assertNull(memoryInfo.getMemoryId());
    }

    @Test
    public void testAllMemoryTypes() {
        String memoryId = "memory-123";
        String content = "Test content";
        boolean includeInResponse = true;

        // Test with SEMANTIC type
        MemoryInfo semanticInfo = new MemoryInfo(memoryId, content, MemoryType.SEMANTIC, includeInResponse);
        assertEquals(MemoryType.SEMANTIC, semanticInfo.getType());

        // Test with USER_PREFERENCE type
        MemoryInfo userPrefInfo = new MemoryInfo(memoryId, content, MemoryType.USER_PREFERENCE, includeInResponse);
        assertEquals(MemoryType.USER_PREFERENCE, userPrefInfo.getType());

        // Test with SUMMARY type
        MemoryInfo summaryInfo = new MemoryInfo(memoryId, content, MemoryType.SUMMARY, includeInResponse);
        assertEquals(MemoryType.SUMMARY, summaryInfo.getType());
    }

    @Test
    public void testContentIsImmutable() {
        String memoryId = "memory-123";
        String content = "Original content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(memoryId, content, type, includeInResponse);

        // Content should remain the same (it's final)
        assertEquals("Original content", memoryInfo.getContent());

        // Verify content getter returns the same value
        String retrievedContent = memoryInfo.getContent();
        assertEquals(content, retrievedContent);
    }

    @Test
    public void testTypeIsImmutable() {
        String memoryId = "memory-123";
        String content = "Test content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(memoryId, content, type, includeInResponse);

        // Type should remain the same (it's final)
        assertEquals(MemoryType.SEMANTIC, memoryInfo.getType());

        // Verify type getter returns the same value
        MemoryType retrievedType = memoryInfo.getType();
        assertEquals(type, retrievedType);
    }

    @Test
    public void testIncludeInResponseIsImmutable() {
        String memoryId = "memory-123";
        String content = "Test content";
        MemoryType type = MemoryType.SEMANTIC;
        boolean includeInResponse = true;

        MemoryInfo memoryInfo = new MemoryInfo(memoryId, content, type, includeInResponse);

        // includeInResponse should remain the same (it's final)
        assertTrue(memoryInfo.isIncludeInResponse());

        // Create another instance with false
        MemoryInfo memoryInfo2 = new MemoryInfo(memoryId, content, type, false);
        assertFalse(memoryInfo2.isIncludeInResponse());
    }

    @Test
    public void testMultipleInstances() {
        // Test that multiple instances maintain their own state
        MemoryInfo info1 = new MemoryInfo("id-1", "content-1", MemoryType.SEMANTIC, true);
        MemoryInfo info2 = new MemoryInfo("id-2", "content-2", MemoryType.USER_PREFERENCE, false);
        MemoryInfo info3 = new MemoryInfo("id-3", "content-3", MemoryType.SUMMARY, true);

        assertEquals("id-1", info1.getMemoryId());
        assertEquals("content-1", info1.getContent());
        assertEquals(MemoryType.SEMANTIC, info1.getType());
        assertTrue(info1.isIncludeInResponse());

        assertEquals("id-2", info2.getMemoryId());
        assertEquals("content-2", info2.getContent());
        assertEquals(MemoryType.USER_PREFERENCE, info2.getType());
        assertFalse(info2.isIncludeInResponse());

        assertEquals("id-3", info3.getMemoryId());
        assertEquals("content-3", info3.getContent());
        assertEquals(MemoryType.SUMMARY, info3.getType());
        assertTrue(info3.isIncludeInResponse());
    }
}
