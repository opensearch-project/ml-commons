/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.utils;

import java.util.HashMap;
import java.util.Map;

public class MapUtils {

    /**
     * Increments the counter for the given key in the specified version.
     * If the key doesn't exist, it initializes the counter to 0.
     *
     * @param version the version of the counter
     * @param key     the key for which the counter needs to be incremented
     */
    public static void incrementCounter(Map<Integer, Map<String, Integer>> versionedCounters, int version, String key) {
        Map<String, Integer> counters = versionedCounters.computeIfAbsent(version, k -> new HashMap<>());
        counters.put(key, counters.getOrDefault(key, -1) + 1);
    }

    /**
     * Retrieves the counter value for the given key in the specified version.
     * If the key doesn't exist, it returns 0.
     *
     * @param version the version of the counter
     * @param key     the key for which the counter needs to be retrieved
     * @return the counter value for the given key
     */
    public static int getCounter(Map<Integer, Map<String, Integer>> versionedCounters, int version, String key) {
        Map<String, Integer> counters = versionedCounters.get(version);
        return counters != null ? counters.getOrDefault(key, -1) : 0;
    }

    /**
     * Increments the counter value for the given key in the provided counters map.
     * If the key does not exist in the map, it is added with an initial counter value of 0.
     *
     * @param counters A map that stores integer counters for each integer key.
     * @param key The integer key for which the counter needs to be incremented.
     */
    public static void incrementCounter(Map<Integer, Integer> counters, int key) {
        counters.put(key, counters.getOrDefault(key, 0) + 1);
    }

    public static int getCounter(Map<Integer, Integer> counters, int key) {
        return counters.getOrDefault(key, 0);
    }

}
