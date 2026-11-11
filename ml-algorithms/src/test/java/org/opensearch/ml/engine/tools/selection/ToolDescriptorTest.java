/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.engine.tools.selection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class ToolDescriptorTest {

    @Test
    public void testCacheKeyIsStableForTheSameInput() {
        ToolDescriptor a = ToolDescriptor.of("SearchIndexTool", "Use this to search an index.");
        ToolDescriptor b = ToolDescriptor.of("SearchIndexTool", "Use this to search an index.");
        assertEquals(a.cacheKey("model-1", null), b.cacheKey("model-1", null));
    }

    @Test
    public void testCacheKeyIsLowercaseHexSha256() {
        String key = ToolDescriptor.of("t", "d").cacheKey("m", null);
        assertEquals(64, key.length());
        assertEquals(key.toLowerCase(Locale.ROOT), key);
        assertTrue(key.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testCacheKeyIsUnchangedByTheHashHelper() {
        // SHA-256 of the length-prefixed fields "1:m", "0:" and "1:d"; cached vectors stay valid across the change.
        assertEquals("9cbbd73010cadb24b2c31991bcba5d8ec193bceb93561e6f43b8c3ef13b0bcc8", ToolDescriptor.of("t", "d").cacheKey("m", null));
    }

    @Test
    public void testCacheKeyChangesWithTheModel() {
        ToolDescriptor descriptor = ToolDescriptor.of("SearchIndexTool", "Use this to search an index.");
        assertNotEquals(descriptor.cacheKey("model-1", null), descriptor.cacheKey("model-2", null));
    }

    @Test
    public void testCacheKeyChangesWithTheDescription() {
        assertNotEquals(
            ToolDescriptor.of("SearchIndexTool", "Use this to search an index.").cacheKey("m", null),
            ToolDescriptor.of("SearchIndexTool", "Use this to search an index, with filters.").cacheKey("m", null)
        );
    }

    @Test
    public void testCacheKeyIgnoresTheToolNameSoIdenticalDescriptionsShareAnEntry() {
        assertEquals(
            ToolDescriptor.of("name-a", "same description").cacheKey("m", null),
            ToolDescriptor.of("name-b", "same description").cacheKey("m", null)
        );
    }

    @Test
    public void testCacheKeyIsNotConfusableAcrossTheModelDescriptionBoundary() {
        assertNotEquals(ToolDescriptor.of("t", "c").cacheKey("ab", null), ToolDescriptor.of("t", "bc").cacheKey("a", null));
    }

    @Test
    public void testNullDescriptionIsTreatedAsEmpty() {
        assertEquals("", ToolDescriptor.of("t", null).getDescription());
    }

    @Test
    public void testNullDescriptionStillProducesAUsableKey() {
        assertEquals(64, ToolDescriptor.of("t", null).cacheKey("m", null).length());
        assertEquals(ToolDescriptor.of("t", null).cacheKey("m", null), ToolDescriptor.of("t", "").cacheKey("m", null));
    }

    @Test
    public void testNullModelIdIsTreatedAsEmpty() {
        assertEquals(ToolDescriptor.of("t", "d").cacheKey(null, null), ToolDescriptor.of("t", "d").cacheKey("", null));
    }

    @Test
    public void testNameIsPreserved() {
        assertEquals("SearchIndexTool", ToolDescriptor.of("SearchIndexTool", "d").getName());
    }

    @Test
    public void testEqualityIsByValue() {
        assertEquals(ToolDescriptor.of("a", "d"), ToolDescriptor.of("a", "d"));
        assertEquals(ToolDescriptor.of("a", "d").hashCode(), ToolDescriptor.of("a", "d").hashCode());
        assertNotEquals(ToolDescriptor.of("a", "d"), ToolDescriptor.of("b", "d"));
    }

    @Test
    public void testUnicodeDescriptionsHashConsistently() {
        // The hash must use a fixed encoding, not the platform default charset.
        ToolDescriptor descriptor = ToolDescriptor.of("t", "índice de búsqueda 中文");
        assertEquals(descriptor.cacheKey("m", null), ToolDescriptor.of("t", "índice de búsqueda 中文").cacheKey("m", null));
        assertEquals(64, descriptor.cacheKey("m", null).length());
    }

    @Test
    public void testEmbeddingTextIsTheDescriptionWhenPresent() {
        assertEquals("Search an index.", ToolDescriptor.of("SearchIndexTool", "Search an index.").embeddingText());
    }

    @Test
    public void testEmbeddingTextFallsBackToTheToolName() {
        assertEquals("SearchIndexTool", ToolDescriptor.of("SearchIndexTool", null).embeddingText());
        assertEquals("SearchIndexTool", ToolDescriptor.of("SearchIndexTool", "").embeddingText());
        assertEquals("SearchIndexTool", ToolDescriptor.of("SearchIndexTool", "   ").embeddingText());
    }

    @Test
    public void testDescriptionlessToolsDoNotCollapseOntoOneCacheEntry() {
        assertNotEquals(
            ToolDescriptor.of("SearchIndexTool", null).cacheKey("m", null),
            ToolDescriptor.of("ListIndexTool", null).cacheKey("m", null)
        );
    }

    @Test
    public void testCacheKeyChangesWithTheTenant() {
        ToolDescriptor descriptor = ToolDescriptor.of("SearchIndexTool", "Search an index.");
        assertNotEquals(descriptor.cacheKey("m", "tenant-a"), descriptor.cacheKey("m", "tenant-b"));
    }

    @Test
    public void testNullTenantIsTreatedAsEmpty() {
        // Single-tenant clusters pass null.
        ToolDescriptor descriptor = ToolDescriptor.of("t", "d");
        assertEquals(descriptor.cacheKey("m", null), descriptor.cacheKey("m", ""));
    }

    @Test
    public void testCacheKeyIsNotConfusableAcrossTheTenantDescriptionBoundary() {
        assertNotEquals(ToolDescriptor.of("t", "c").cacheKey("m", "ab"), ToolDescriptor.of("t", "bc").cacheKey("m", "a"));
    }

    @Test
    public void testNewlinesInFieldsCannotShiftTheBoundary() {
        assertNotEquals(ToolDescriptor.of("x", "bar").cacheKey("m", "t1\nfoo"), ToolDescriptor.of("x", "foo\nbar").cacheKey("m", "t1"));
        assertNotEquals(ToolDescriptor.of("x", "t1\nbar").cacheKey("m\n", null), ToolDescriptor.of("x", "bar").cacheKey("m", "\nt1"));
    }
}
