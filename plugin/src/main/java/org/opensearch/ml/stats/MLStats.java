/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.ml.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import lombok.Getter;

import org.opensearch.ml.stats.suppliers.CounterSupplier;

/**
 * This class is the main entry-point for access to the stats that the ML plugin keeps track of.
 */
public class MLStats {
    @Getter
    private Map<String, MLStat<?>> stats;

    /**
     * Constructor
     *
     * @param stats Map of the stats that are to be kept
     */
    public MLStats(Map<String, MLStat<?>> stats) {
        this.stats = stats;
    }

    /**
     * Get individual stat by stat name
     *
     * @param key Name of stat
     * @return ADStat
     * @throws IllegalArgumentException thrown on illegal statName
     */
    public MLStat<?> getStat(String key) throws IllegalArgumentException {
        if (!stats.keySet().contains(key)) {
            throw new IllegalArgumentException("Stat \"" + key + "\" does not exist");
        }
        return stats.get(key);
    }

    /**
     * Get stat or create counter stat if absent.
     * @param key stat key
     * @return existing MLStat or new MLStat
     */
    public MLStat<?> createCounterStatIfAbsent(String key) {
        return createStatIfAbsent(key, () -> new MLStat<>(false, new CounterSupplier()));
    }

    /**
     * Get stat or create if absent.
     * @param key stat key
     * @param supplier supplier to create MLStat
     * @return existing MLStat or new MLStat
     */
    public synchronized MLStat<?> createStatIfAbsent(String key, Supplier<MLStat> supplier) {
        return stats.computeIfAbsent(key, k -> supplier.get());
    }

    /**
     * Get a map of the stats that are kept at the node level
     *
     * @return Map of stats kept at the node level
     */
    public Map<String, MLStat<?>> getNodeStats() {
        return getClusterOrNodeStats(false);
    }

    /**
     * Get a map of the stats that are kept at the cluster level
     *
     * @return Map of stats kept at the cluster level
     */
    public Map<String, MLStat<?>> getClusterStats() {
        return getClusterOrNodeStats(true);
    }

    private Map<String, MLStat<?>> getClusterOrNodeStats(Boolean getClusterStats) {
        Map<String, MLStat<?>> statsMap = new HashMap<>();

        for (Map.Entry<String, MLStat<?>> entry : stats.entrySet()) {
            if (entry.getValue().isClusterLevel() == getClusterStats) {
                statsMap.put(entry.getKey(), entry.getValue());
            }
        }
        return statsMap;
    }
}
