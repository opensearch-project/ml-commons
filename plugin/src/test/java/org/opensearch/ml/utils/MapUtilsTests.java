/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.opensearch.ml.utils;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class MapUtilsTests {

    @Test
    public void testIncrementCounterForVersionedCounters() {
        Map<Integer, Map<String, Integer>> versionedCounters = new HashMap<>();

        MapUtils.incrementCounter(versionedCounters, 0, "key1");
        assertEquals(0, (int) versionedCounters.get(0).get("key1"));

        // Test incrementing counter for an existing version and key
        MapUtils.incrementCounter(versionedCounters, 0, "key1");
        assertEquals(1, (int) versionedCounters.get(0).get("key1"));

        // Test incrementing counter for a new key in an existing version
        MapUtils.incrementCounter(versionedCounters, 0, "key2");
        assertEquals(0, (int) versionedCounters.get(0).get("key2"));

        // Test incrementing counter for a new version
        MapUtils.incrementCounter(versionedCounters, 1, "key3");
        assertEquals(0, (int) versionedCounters.get(1).get("key3"));
    }

    @Test
    public void testIncrementCounterForIntegerCounters() {
        Map<Integer, Integer> counters = new HashMap<>();

        // Test incrementing counter for a new key
        MapUtils.incrementCounter(counters, 1);
        assertEquals(1, (int) counters.get(1));

        // Test incrementing counter for an existing key
        MapUtils.incrementCounter(counters, 1);
        assertEquals(2, (int) counters.get(1));

        // Test incrementing counter for a new key
        MapUtils.incrementCounter(counters, 2);
        assertEquals(1, (int) counters.get(2));
    }

    @Test
    public void testGetCounterForVersionedCounters() {
        Map<Integer, Map<String, Integer>> versionedCounters = new HashMap<>();
        versionedCounters.put(0, new HashMap<>());
        versionedCounters.put(1, new HashMap<>());
        versionedCounters.get(0).put("key1", 5);
        versionedCounters.get(1).put("key2", 10);

        // Test getting counter for an existing key
        assertEquals(5, MapUtils.getCounter(versionedCounters, 0, "key1"));
        assertEquals(10, MapUtils.getCounter(versionedCounters, 1, "key2"));

        // Test getting counter for a non-existing key
        assertEquals(-1, MapUtils.getCounter(versionedCounters, 0, "key3"));
        assertEquals(0, MapUtils.getCounter(versionedCounters, 2, "key4"));
    }

    @Test
    public void testGetCounterForIntegerCounters() {
        Map<Integer, Integer> counters = new HashMap<>();
        counters.put(1, 5);
        counters.put(2, 10);

        // Test getting counter for an existing key
        assertEquals(5, MapUtils.getCounter(counters, 1));
        assertEquals(10, MapUtils.getCounter(counters, 2));

        // Test getting counter for a non-existing key
        assertEquals(0, MapUtils.getCounter(counters, 3));
    }
}
