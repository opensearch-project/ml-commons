/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.helper;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.ml.common.memorycontainer.MemoryStrategy;
import org.opensearch.ml.common.memorycontainer.MemoryStrategyType;

public class StrategyMergeHelperTests {

    private List<MemoryStrategy> existingStrategies;

    @Before
    public void setUp() {
        // Create some existing strategies for testing
        existingStrategies = new ArrayList<>();

        MemoryStrategy semantic = MemoryStrategy
            .builder()
            .id("semantic_12345678")
            .enabled(true)
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        MemoryStrategy userPref = MemoryStrategy
            .builder()
            .id("user_preference_87654321")
            .enabled(false)
            .type(MemoryStrategyType.USER_PREFERENCE)
            .namespace(Arrays.asList("user_id", "session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        existingStrategies.add(semantic);
        existingStrategies.add(userPref);
    }

    @Test
    public void testMergeStrategies_UpdateExistingById() {
        // Test updating an existing strategy by ID
        MemoryStrategy update = MemoryStrategy
            .builder()
            .id("semantic_12345678")
            .enabled(false)  // Change enabled to false
            .build();

        List<MemoryStrategy> updates = Arrays.asList(update);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        assertEquals(2, result.size());
        MemoryStrategy updatedStrategy = result.stream().filter(s -> s.getId().equals("semantic_12345678")).findFirst().orElse(null);

        assertNotNull(updatedStrategy);
        assertFalse(updatedStrategy.getEnabled());  // Should be updated
        assertEquals(MemoryStrategyType.SEMANTIC, updatedStrategy.getType());  // Should be preserved
        assertEquals(Arrays.asList("user_id"), updatedStrategy.getNamespace());  // Should be preserved
    }

    @Test
    public void testMergeStrategies_PartialFieldUpdate() {
        // Test updating only specific fields, others should be preserved
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("threshold", 0.8);

        MemoryStrategy update = MemoryStrategy.builder().id("user_preference_87654321").strategyConfig(newConfig).build();

        List<MemoryStrategy> updates = Arrays.asList(update);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        MemoryStrategy updatedStrategy = result.stream().filter(s -> s.getId().equals("user_preference_87654321")).findFirst().orElse(null);

        assertNotNull(updatedStrategy);
        assertEquals(newConfig, updatedStrategy.getStrategyConfig());  // Should be updated
        assertFalse(updatedStrategy.getEnabled());  // Should be preserved (was false)
        assertEquals(MemoryStrategyType.USER_PREFERENCE, updatedStrategy.getType());  // Should be preserved
        assertEquals(Arrays.asList("user_id", "session_id"), updatedStrategy.getNamespace());  // Should be preserved
    }

    @Test
    public void testMergeStrategies_AddNewStrategyWithoutId() {
        // Test adding a new strategy without ID (should auto-generate)
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SUMMARY)
            .namespace(Arrays.asList("agent_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        assertEquals(3, result.size());  // Should have 3 strategies now

        MemoryStrategy addedStrategy = result.stream().filter(s -> s.getType() == MemoryStrategyType.SUMMARY).findFirst().orElse(null);

        assertNotNull(addedStrategy);
        assertNotNull(addedStrategy.getId());  // Should have auto-generated ID
        assertTrue(addedStrategy.getId().startsWith("summary_"));  // Should have type prefix
        assertTrue(addedStrategy.getEnabled());  // Should default to true
        assertEquals(Arrays.asList("agent_id"), addedStrategy.getNamespace());
    }

    @Test
    public void testMergeStrategies_NewStrategyDefaultsToEnabledTrue() {
        // Test that new strategies without enabled field default to true
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SUMMARY)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        MemoryStrategy addedStrategy = result.stream().filter(s -> s.getType() == MemoryStrategyType.SUMMARY).findFirst().orElse(null);

        assertNotNull(addedStrategy);
        assertTrue(addedStrategy.getEnabled());  // Should default to true
    }

    @Test
    public void testMergeStrategies_NewStrategyWithExplicitEnabledFalse() {
        // Test that new strategies can be explicitly set to enabled=false
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SUMMARY)
            .enabled(false)  // Explicitly set to false
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        MemoryStrategy addedStrategy = result.stream().filter(s -> s.getType() == MemoryStrategyType.SUMMARY).findFirst().orElse(null);

        assertNotNull(addedStrategy);
        assertFalse(addedStrategy.getEnabled());  // Should respect explicit false
    }

    @Test
    public void testMergeStrategies_StrategyIdNotFound() {
        // Test error when trying to update non-existent strategy ID
        MemoryStrategy update = MemoryStrategy.builder().id("nonexistent_id").enabled(true).build();

        List<MemoryStrategy> updates = Arrays.asList(update);

        try {
            StrategyMergeHelper.mergeStrategies(existingStrategies, updates);
            fail("Expected OpenSearchStatusException");
        } catch (OpenSearchStatusException e) {
            assertEquals(RestStatus.NOT_FOUND, e.status());
            assertTrue(e.getMessage().contains("Strategy with id nonexistent_id not found"));
        }
    }

    @Test
    public void testMergeStrategies_CannotChangeStrategyType() {
        // Test error when trying to change strategy type
        MemoryStrategy update = MemoryStrategy
            .builder()
            .id("semantic_12345678")
            .type(MemoryStrategyType.USER_PREFERENCE)  // Try to change type
            .build();

        List<MemoryStrategy> updates = Arrays.asList(update);

        try {
            StrategyMergeHelper.mergeStrategies(existingStrategies, updates);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot change strategy type"));
            assertTrue(e.getMessage().contains("SEMANTIC"));
            assertTrue(e.getMessage().contains("USER_PREFERENCE"));
        }
    }

    @Test
    public void testMergeStrategies_PreserveUnmodifiedStrategies() {
        // Test that strategies not mentioned in updates are preserved unchanged
        MemoryStrategy update = MemoryStrategy.builder().id("semantic_12345678").enabled(false).build();

        List<MemoryStrategy> updates = Arrays.asList(update);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        // Find the unmodified strategy
        MemoryStrategy unmodified = result.stream().filter(s -> s.getId().equals("user_preference_87654321")).findFirst().orElse(null);

        assertNotNull(unmodified);
        assertFalse(unmodified.getEnabled());  // Should be unchanged
        assertEquals(MemoryStrategyType.USER_PREFERENCE, unmodified.getType());
        assertEquals(Arrays.asList("user_id", "session_id"), unmodified.getNamespace());
    }

    @Test
    public void testMergeStrategies_MultipleUpdates() {
        // Test merging multiple updates in one call
        MemoryStrategy update1 = MemoryStrategy.builder().id("semantic_12345678").enabled(false).build();

        MemoryStrategy update2 = MemoryStrategy.builder().id("user_preference_87654321").enabled(true).build();

        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SUMMARY)
            .namespace(Arrays.asList("session_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(update1, update2, newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        assertEquals(3, result.size());

        // Verify first update
        MemoryStrategy updated1 = result.stream().filter(s -> s.getId().equals("semantic_12345678")).findFirst().orElse(null);
        assertNotNull(updated1);
        assertFalse(updated1.getEnabled());

        // Verify second update
        MemoryStrategy updated2 = result.stream().filter(s -> s.getId().equals("user_preference_87654321")).findFirst().orElse(null);
        assertNotNull(updated2);
        assertTrue(updated2.getEnabled());

        // Verify new strategy
        MemoryStrategy added = result.stream().filter(s -> s.getType() == MemoryStrategyType.SUMMARY).findFirst().orElse(null);
        assertNotNull(added);
        assertTrue(added.getEnabled());
    }

    @Test
    public void testMergeStrategies_NullExistingList() {
        // Test with null existing strategies list
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(null, updates);

        assertEquals(1, result.size());
        assertEquals(MemoryStrategyType.SEMANTIC, result.get(0).getType());
    }

    @Test
    public void testMergeStrategies_EmptyExistingList() {
        // Test with empty existing strategies list
        MemoryStrategy newStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(Arrays.asList("user_id"))
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(newStrategy);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(new ArrayList<>(), updates);

        assertEquals(1, result.size());
        assertEquals(MemoryStrategyType.SEMANTIC, result.get(0).getType());
    }

    @Test
    public void testMergeStrategies_NullUpdatesList() {
        // Test with null updates list - should return existing strategies
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, null);

        assertEquals(existingStrategies.size(), result.size());
        assertEquals(existingStrategies, result);
    }

    @Test
    public void testMergeStrategies_EmptyUpdatesList() {
        // Test with empty updates list - should return existing strategies
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, new ArrayList<>());

        assertEquals(existingStrategies.size(), result.size());
        assertEquals(existingStrategies, result);
    }

    @Test
    public void testMergeStrategies_UpdateNamespace() {
        // Test updating namespace field
        MemoryStrategy update = MemoryStrategy
            .builder()
            .id("semantic_12345678")
            .namespace(Arrays.asList("user_id", "agent_id"))  // Add agent_id
            .build();

        List<MemoryStrategy> updates = Arrays.asList(update);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        MemoryStrategy updated = result.stream().filter(s -> s.getId().equals("semantic_12345678")).findFirst().orElse(null);

        assertNotNull(updated);
        assertEquals(Arrays.asList("user_id", "agent_id"), updated.getNamespace());
    }

    @Test
    public void testMergeStrategies_UpdateStrategyConfig() {
        // Test updating strategyConfig field
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("threshold", 0.75);
        newConfig.put("max_results", 10);

        MemoryStrategy update = MemoryStrategy.builder().id("semantic_12345678").strategyConfig(newConfig).build();

        List<MemoryStrategy> updates = Arrays.asList(update);
        List<MemoryStrategy> result = StrategyMergeHelper.mergeStrategies(existingStrategies, updates);

        MemoryStrategy updated = result.stream().filter(s -> s.getId().equals("semantic_12345678")).findFirst().orElse(null);

        assertNotNull(updated);
        assertEquals(newConfig, updated.getStrategyConfig());
    }

    // Note: Test for invalid strategy type removed - type is now @NonNull enum,
    // so invalid types cannot be constructed via builder

    // Note: Test for missing namespace removed - namespace is now @NonNull,
    // so null namespace cannot be set via builder

    @Test
    public void testMergeStrategies_EmptyNamespace() {
        // Test adding new strategy with empty namespace array
        MemoryStrategy emptyNamespaceStrategy = MemoryStrategy
            .builder()
            .type(MemoryStrategyType.SEMANTIC)
            .namespace(new ArrayList<>())
            .strategyConfig(new HashMap<>())
            .build();

        List<MemoryStrategy> updates = Arrays.asList(emptyNamespaceStrategy);

        try {
            StrategyMergeHelper.mergeStrategies(existingStrategies, updates);
            fail("Expected IllegalArgumentException for empty namespace");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("namespace is required"));
        }
    }
}
